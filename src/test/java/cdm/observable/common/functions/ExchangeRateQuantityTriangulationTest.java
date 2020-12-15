package cdm.observable.common.functions;

import org.isda.cdm.functions.AbstractFunctionTest;

public class ExchangeRateQuantityTriangulationTest extends AbstractFunctionTest {

	private static final String FX_DIR = "result-json-files/products/fx/";

//	@Inject
//	private ExchangeRateQuantityTriangulation func;
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess1() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex01-fx-spot.json", 10000000, 14800000, 1.48, 2);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess2() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex02-spot-cross-w-side-rates.json", 10000000, 6300680, 0.630068, 6);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess3() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex03-fx-fwd.json", 10000000, 9175000, 0.9175, 4);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess4() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex04-fx-fwd-w-settlement.json", 10000000, 14643000, 1.4643, 4);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess5() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex05-fx-fwd-w-ssi.json", 10000000, 9175000, 0.9175, 4);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess6() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex06-fx-fwd-w-splits.json", 13000000, 14393600, 1.1072, 4);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess7() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex07-non-deliverable-forward.json", 10000000, 434000000, 43.40, 1);
//	}
//
//	@Test
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess8() throws IOException {
//		shouldTriangulateFxRateAndNotionalAndReturnSuccess("fx-ex28-non-deliverable-w-disruption.json", 3000000, 2307000, 0.7690, 1);
//	}
//
//	void shouldTriangulateFxRateAndNotionalAndReturnSuccess(String filename, int quantity1, int quantity2, double rate, int scale) throws IOException {
//		TradeState tradeState = getObject(TradeState.class, FX_DIR + filename);
//		TradableProduct tradableProduct = tradeState.getTrade().getTradableProduct();
//		List<PriceNotation> priceNotation = tradableProduct.getPriceNotation();
//		List<QuantityNotation> quantityNotation = tradableProduct.getQuantityNotation();
//
//		// check result
//		assertTrue(func.evaluate(priceNotation, quantityNotation));
//		// check aliases
//		assertThat(func.quantity1(priceNotation, quantityNotation).get(), is(BigDecimal.valueOf(quantity1)));
//		assertThat(func.quantity2(priceNotation, quantityNotation).get(), is(BigDecimal.valueOf(quantity2)));
//		assertThat(func.rate(priceNotation, quantityNotation).get().setScale(scale, RoundingMode.HALF_UP), is(BigDecimal.valueOf(rate).setScale(scale, RoundingMode.HALF_UP)));
//	}
}
