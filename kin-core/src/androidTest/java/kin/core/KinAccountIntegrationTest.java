package kin.core;


import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kin.core.exception.AccountDeletedException;
import kin.core.exception.AccountNotActivatedException;
import kin.core.exception.AccountNotFoundException;
import kin.core.exception.InsufficientKinException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.stellar.sdk.Memo;
import org.stellar.sdk.MemoText;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.TransactionResponse;

@SuppressWarnings({"deprecation", "ConstantConditions"})
public class KinAccountIntegrationTest {

    private static final String TEST_NETWORK_URL = "https://horizon-testnet.stellar.org";
    private static FakeKinIssuer fakeKinIssuer;
    private KinClient kinClient;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @BeforeClass
    static public void setupKinIssuer() throws IOException {
        fakeKinIssuer = new FakeKinIssuer();
    }

    private class TestServiceProvider extends ServiceProvider {

        TestServiceProvider() {
            super(TEST_NETWORK_URL, NETWORK_ID_TEST);
        }

        @Override
        protected String getAssetCode() {
            return "KIN";
        }

        @Override
        protected String getIssuerAccountId() {
            return fakeKinIssuer.getAccountId();
        }
    }


    @Before
    public void setup() throws IOException {
        ServiceProvider serviceProvider = new TestServiceProvider();
        kinClient = new KinClient(InstrumentationRegistry.getTargetContext(), serviceProvider);
        kinClient.clearAllAccounts();
    }

    @After
    public void teardown() {
        kinClient.clearAllAccounts();
    }

    @Test
    @LargeTest
    public void getBalanceSync_AccountNotCreated_AccountNotFoundException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();

        expectedEx.expect(AccountNotFoundException.class);
        expectedEx.expectMessage(kinAccount.getPublicAddress());
        kinAccount.getBalanceSync();
    }

    @Test
    @LargeTest
    public void getBalanceSync_AccountNotActivated_AccountNotActivatedException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccount.getPublicAddress());

        expectedEx.expect(AccountNotActivatedException.class);
        expectedEx.expectMessage(kinAccount.getPublicAddress());
        kinAccount.getBalanceSync();
    }

    @Test
    @LargeTest
    public void getBalanceSync_FundedAccount_GotBalance() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccount.getPublicAddress());

        kinAccount.activateSync();
        assertThat(kinAccount.getBalanceSync().value(), equalTo(new BigDecimal("0.0000000")));

        fakeKinIssuer.fundWithKin(kinAccount.getPublicAddress(), "3.1415926");
        assertThat(kinAccount.getBalanceSync().value(), equalTo(new BigDecimal("3.1415926")));
    }

    @Test
    @LargeTest
    public void activateSync_AccountNotCreated_AccountNotFoundException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();

        expectedEx.expect(AccountNotFoundException.class);
        expectedEx.expectMessage(kinAccount.getPublicAddress());
        kinAccount.activateSync();
    }

    @Test
    @LargeTest
    public void sendTransaction() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountSender.activateSync();
        kinAccountReceiver.activateSync();
        fakeKinIssuer.fundWithKin(kinAccountSender.getPublicAddress(), "100");

        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
        assertThat(kinAccountSender.getBalanceSync().value(), equalTo(new BigDecimal("78.8770000")));
        assertThat(kinAccountReceiver.getBalanceSync().value(), equalTo(new BigDecimal("21.1230000")));
    }

    @Test
    @LargeTest
    public void sendTransaction_WithMemo() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());
        String expectedMemo = "fake memo";

        kinAccountSender.activateSync();
        kinAccountReceiver.activateSync();
        fakeKinIssuer.fundWithKin(kinAccountSender.getPublicAddress(), "100");

        TransactionId transactionId = kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"),
                expectedMemo);
        assertThat(kinAccountSender.getBalanceSync().value(), equalTo(new BigDecimal("78.8770000")));
        assertThat(kinAccountReceiver.getBalanceSync().value(), equalTo(new BigDecimal("21.1230000")));

        Server server = new Server(TEST_NETWORK_URL);
        TransactionResponse transaction = server.transactions().transaction(transactionId.id());
        Memo actualMemo = transaction.getMemo();
        assertThat(actualMemo, is(instanceOf(MemoText.class)));
        assertThat(((MemoText) actualMemo).getText(), equalTo(expectedMemo));
    }

    @Test
    @LargeTest
    public void sendTransaction_ReceiverAccountNotCreated_AccountNotFoundException() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());

        expectedEx.expect(AccountNotFoundException.class);
        expectedEx.expectMessage(kinAccountReceiver.getPublicAddress());
        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
    }

    @Test
    @LargeTest
    public void sendTransaction_SenderAccountNotCreated_AccountNotFoundException() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());
        kinAccountReceiver.activateSync();

        expectedEx.expect(AccountNotFoundException.class);
        expectedEx.expectMessage(kinAccountSender.getPublicAddress());
        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
    }

    @Test
    @LargeTest
    public void sendTransaction_ReceiverAccountNotActivated_AccountNotFoundException() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountSender.activateSync();

        expectedEx.expect(AccountNotActivatedException.class);
        expectedEx.expectMessage(kinAccountReceiver.getPublicAddress());
        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
    }

    @Test
    @LargeTest
    public void sendTransaction_SenderAccountNotActivated_AccountNotFoundException() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountReceiver.activateSync();

        expectedEx.expect(AccountNotActivatedException.class);
        expectedEx.expectMessage(kinAccountSender.getPublicAddress());
        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
    }

    @Test
    @LargeTest
    public void createPaymentWatcher_WatchReceiver_PaymentEvent() throws Exception {
        watchPayment(false);
    }

    @Test
    @LargeTest
    public void createPaymentWatcher_WatchSender_PaymentEvent() throws Exception {
        watchPayment(true);
    }

    private void watchPayment(boolean watchSender) throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountSender.activateSync();
        kinAccountReceiver.activateSync();
        fakeKinIssuer.fundWithKin(kinAccountSender.getPublicAddress(), "100");

        final CountDownLatch latch = new CountDownLatch(1);
        final List<PaymentInfo> actualResults = new ArrayList<>();
        KinAccount accountToWatch = watchSender ? kinAccountSender : kinAccountReceiver;
        accountToWatch.createPaymentWatcher().start(new WatcherListener<PaymentInfo>() {
            @Override
            public void onEvent(PaymentInfo data) {
                actualResults.add(data);
                latch.countDown();
            }
        });

        BigDecimal expectedAmount = new BigDecimal("21.123");
        String expectedMemo = "memo";
        TransactionId expectedTransactionId = kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), expectedAmount, expectedMemo);

        latch.await(10, TimeUnit.SECONDS);
        assertThat(actualResults.size(), equalTo(1));
        PaymentInfo paymentInfo = actualResults.get(0);
        assertThat(paymentInfo.amount(), equalTo(expectedAmount));
        assertThat(paymentInfo.destinationPublicKey(), equalTo(kinAccountReceiver.getPublicAddress()));
        assertThat(paymentInfo.sourcePublicKey(), equalTo(kinAccountSender.getPublicAddress()));
        assertThat(paymentInfo.memo(), equalTo(expectedMemo));
        assertThat(paymentInfo.hash().id(), equalTo(expectedTransactionId.id()));
    }

    @Test
    @LargeTest
    public void createPaymentWatcher_StopWatching_NoEvents() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountSender.activateSync();
        kinAccountReceiver.activateSync();
        fakeKinIssuer.fundWithKin(kinAccountSender.getPublicAddress(), "100");

        final CountDownLatch latch = new CountDownLatch(1);

        PaymentWatcher paymentWatcher = kinAccountReceiver.createPaymentWatcher();
        paymentWatcher.start(new WatcherListener<PaymentInfo>() {
            @Override
            public void onEvent(PaymentInfo data) {
                fail("should not get eny event!");
                latch.countDown();
            }
        });
        paymentWatcher.stop();

        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"), null);

        latch.await(15, TimeUnit.SECONDS);
    }

    @Test
    @LargeTest
    public void sendTransaction_NotEnoughKin_TransactionFailedException() throws Exception {
        KinAccount kinAccountSender = kinClient.addAccount();
        KinAccount kinAccountReceiver = kinClient.addAccount();
        fakeKinIssuer.createAccount(kinAccountSender.getPublicAddress());
        fakeKinIssuer.createAccount(kinAccountReceiver.getPublicAddress());

        kinAccountSender.activateSync();
        kinAccountReceiver.activateSync();

        expectedEx.expect(InsufficientKinException.class);
        kinAccountSender
            .sendTransactionSync(kinAccountReceiver.getPublicAddress(), new BigDecimal("21.123"));
    }

    @Test(expected = AccountDeletedException.class)
    public void activateSync_DeletedAccount_AccountDeletedException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        kinClient.deleteAccount(0);
        kinAccount.activateSync();
    }

    @Test(expected = AccountDeletedException.class)
    public void getBalanceSync_DeletedAccount_AccountDeletedException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        kinClient.deleteAccount(0);
        kinAccount.getBalanceSync();
    }

    @Test(expected = AccountDeletedException.class)
    public void sendTransactionSync_DeletedAccount_AccountDeletedException() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        kinClient.deleteAccount(0);
        kinAccount.sendTransactionSync("GBA2XHZRUAHEL4DZX7XNHR7HLBAUYPRNKLD2PIUKWV2LVVE6OJT4NDLM",
            new BigDecimal(10));
    }

    @Test
    public void getPublicAddress_DeletedAccount_EmptyPublicAddress() throws Exception {
        KinAccount kinAccount = kinClient.addAccount();
        kinClient.deleteAccount(0);
        assertNull(kinAccount.getPublicAddress());
    }

}
