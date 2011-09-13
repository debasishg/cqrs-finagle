package net.debasishg.domain.trade.service

import java.util.{Date, Calendar}
import com.twitter.util._
import com.twitter.concurrent.ChannelSource
import java.util.concurrent.Executors

import net.debasishg.domain.trade.model._
import TradeModel._

// command events
// processed by EventStore and forwarded to QueryStore
case class TradeEnriched(trade: Trade, closure: TradeEvent)
case class ValueDateAdded(trade: Trade, closure: TradeEvent)

case object Closing
case object Snapshot
case object QueryAllTrades

class TradingClient(val eventStore: EventStore = new EventStore(List(new QueryStore))) {
  // create a trade : wraps the model method
  def newTrade(account: Account, instrument: Instrument, refNo: String, market: Market,
    unitPrice: BigDecimal, quantity: BigDecimal, tradeDate: Date = Calendar.getInstance.getTime) =
      makeTrade(account, instrument, refNo, market, unitPrice, quantity) // @tofix : additional args

  // refer To Mock a Mockingbird
  private[service] def kestrel[T](trade: T, proc: T => T)(effect: => Unit) = Future[T] {
    val t = proc(trade)
    effect
    t
  }

  def service[T <: TradeEvent](trade: Trade, event: T) = kestrel(trade, event) {
    eventStore.store(trade, event)
  }

  def doEnrichTrade(trade: Trade) = service(trade, enrichTrade)
  def doAddValueDate(trade: Trade) = service(trade, addValueDate)

  // non-blocking: returns a Future
  def getCommandSnapshot = Future[Seq[Trade]]{ eventStore.snapshot.toSeq }

  def getAllTrades = eventStore.listeners.head.asInstanceOf[QueryStore].allTrades

  // non-blocking and composition of futures
  // a function to operate on every trade in the list of trades
  def onTrades[T](trades: Seq[Trade])(fn: Trade => T): Future[Seq[T]] = { 
    val futurePool = FuturePool(Executors.newFixedThreadPool(4))
    futurePool{ trades.map(trade => fn(trade)) }
  }

  def sumTaxFees(trades: Seq[Trade]) = { 
    val maybeTaxFees =
      onTrades[BigDecimal](trades) {trade =>
        trade.taxFees
             .map(_.map(_._2).foldLeft(BigDecimal(0))(_ + _))
             .getOrElse(sys.error("cannot get tax/fees"))
      }
    maybeTaxFees onSuccess { _.sum }
  }
}

case class EventStore(listeners: List[Listener] = List.empty) {

  // set up observers
  private val source = new ChannelSource[(Trade, TradeEvent)]
  source.respond { msg =>
    Future { listeners.foreach(_.listen(msg._1, msg._2)) }
  }

  import scala.collection.JavaConversions._ 
  private final val events: collection.mutable.ConcurrentMap[Trade, List[TradeEvent]] = 
    new java.util.concurrent.ConcurrentHashMap[Trade, List[TradeEvent]]

  def store(trade: Trade, evt: => TradeEvent) = {
    events += ((trade, events.getOrElse(trade, List.empty[TradeEvent]) :+ evt))
    source send (trade, evt)
  }

  def snapshot = events.keys.map {trade =>
    events(trade).foldLeft(trade)((t, e) => e(t))}
}

sealed trait Listener {
  def listen(trade: Trade, evt: => TradeEvent)
}

class QueryStore extends Listener {
  private var trades = new collection.immutable.TreeSet[Trade]()(Ordering.by(_.refNo))

  def listen(trade: Trade, evt: => TradeEvent) =
    trades += trades.find(_ == trade).map(evt(_)).getOrElse(evt(trade))

  def allTrades = trades.toList
}

