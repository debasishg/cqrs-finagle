package net.debasishg.domain.trade.service

import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

@RunWith(classOf[JUnitRunner])
class TradingServiceSpec extends Spec with ShouldMatchers {

  import java.util.Calendar
  import com.twitter.util.Future
  import net.debasishg.domain.trade.model._
  import net.debasishg.domain.trade.model.TradeModel._

  describe("trading service that does computation with futures") {
    it("should compute tax/fees over all trades in nonblocking mode") {
      val c = new TradingClient
      import c._

      // make trades
      val trds = Seq(newTrade("a-123", "google", "r-123", HongKong, 12.25, 200).toOption.get,
        newTrade("a-124", "ibm", "r-124", Tokyo, 22.25, 250).toOption.get,
        newTrade("a-125", "cisco", "r-125", NewYork, 20.25, 150).toOption.get,
        newTrade("a-126", "ibm", "r-127", Singapore, 22.25, 250).toOption.get)

      // domain logic
      val enrichedTradeFutures: Seq[Future[Trade]] = 
        trds.map(trd => 
          doAddValueDate(trd) flatMap(t => doEnrichTrade(t)))
      val enrichedTradesFuture: Future[Seq[Trade]] = Future.collect(enrichedTradeFutures)

      // sum all tax/fees
      enrichedTradesFuture onSuccess { trades => sumTaxFees(trades)().sum should equal(6370.625) }
      getCommandSnapshot onSuccess { _.foreach(println) }
      getCommandSnapshot onSuccess { trades => sumTaxFees(trades)().sum should equal(6370.625) }
    }
  }
}
