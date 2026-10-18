package com.geprofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The plugin's own arithmetic, for the direct-to-Wiki mode.
 */
public class GePricesServiceTest
{
	@Test
	public void cheapItemsPayNoTax()
	{
		assertEquals(0, GePricesService.taxFor(49));
	}

	@Test
	public void taxIsTwoPercentRoundedDown()
	{
		assertEquals(2, GePricesService.taxFor(149));
		assertEquals(20, GePricesService.taxFor(1_000));
	}

	@Test
	public void taxIsCapped()
	{
		assertEquals(5_000_000, GePricesService.taxFor(2_000_000_000));
	}

	@Test
	public void aPriceTheGameCannotHoldIsRejectedForThatItemOnly()
	{
		assertTrue(GePricesService.fitsInGame(200L));
		assertTrue(GePricesService.fitsInGame((long) Integer.MAX_VALUE));
		assertFalse(GePricesService.fitsInGame(9_100_000_000L));
		assertFalse(GePricesService.fitsInGame(0L));
		assertFalse(GePricesService.fitsInGame(null));
	}
}
