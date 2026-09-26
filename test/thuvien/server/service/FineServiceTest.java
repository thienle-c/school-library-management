package thuvien.server.service;

import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import thuvien.server.service.impl.FineServiceImpl;

public class FineServiceTest {

    private FineService fineService;

    @Before
    public void setUp() {
        fineService = new FineServiceImpl(null);
    }

    @Test
    public void testCalculateOverdueFineZeroDays() {
        BigDecimal fine = fineService.calculateOverdueFine(0);
        Assert.assertEquals(BigDecimal.ZERO, fine);
    }

    @Test
    public void testCalculateOverdueFineNegativeDays() {
        BigDecimal fine = fineService.calculateOverdueFine(-5);
        Assert.assertEquals(BigDecimal.ZERO, fine);
    }

    @Test
    public void testCalculateOverdueFinePositiveDays() {
        // Daily rate is 5000.00 VND
        BigDecimal fine1 = fineService.calculateOverdueFine(1);
        Assert.assertEquals(new BigDecimal("5000.00"), fine1);

        BigDecimal fine3 = fineService.calculateOverdueFine(3);
        Assert.assertEquals(new BigDecimal("15000.00"), fine3);

        BigDecimal fine10 = fineService.calculateOverdueFine(10);
        Assert.assertEquals(new BigDecimal("50000.00"), fine10);
    }
}
