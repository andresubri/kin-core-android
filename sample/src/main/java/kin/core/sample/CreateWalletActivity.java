package kin.core.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import kin.core.KinClient;
import kin.core.exception.CreateAccountException;
import kin.sdk.core.sample.R;

/**
 * This activity is displayed only if there is no existing account stored on device for the given network
 * The activity will just display a button to create an account
 */
public class CreateWalletActivity extends BaseActivity {

    public static final String TAG = CreateWalletActivity.class.getSimpleName();

    public static Intent getIntent(Context context) {
        return new Intent(context, CreateWalletActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_wallet_activity);
        initWidgets();
    }

    private void initWidgets() {
        View createWallet = findViewById(R.id.btn_create_wallet);
        if (isMainNet()) {
            createWallet.setBackgroundResource(R.drawable.button_main_network_bg);
        }
        createWallet.setOnClickListener(view -> createAccount());
    }

    private void createAccount() {
        try {
            final KinClient kinClient = getKinClient();
            kinClient.addAccount();
            startActivity(WalletActivity.getIntent(this));
        } catch (CreateAccountException e) {
            KinAlertDialog.createErrorDialog(this, e.getMessage()).show();
        }
    }

    @Override
    Intent getBackIntent() {
        return ChooseNetworkActivity.getIntent(this);
    }

    @Override
    int getActionBarTitleRes() {
        return R.string.create_wallet;
    }

}
