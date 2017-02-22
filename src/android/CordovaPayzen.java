package com.eliberty.cordova.plugin.payzen;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;

import com.lyranetwork.mpos.sdk.MCurrency;
import com.lyranetwork.mpos.sdk.MCustomer;
import com.lyranetwork.mpos.sdk.MTransaction;
import com.lyranetwork.mpos.sdk.MTransactionType;
import com.lyranetwork.mpos.sdk.MposResult;
import com.lyranetwork.mpos.sdk.MposSDK;
import com.lyranetwork.mpos.sdk.error.MposException;
import com.lyranetwork.mpos.sdk.process.manager.Result;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.getsentry.raven.Raven;
import com.getsentry.raven.RavenFactory;

/**
 * CordovaPayzen is a PhoneGap/Cordova plugin that bridges Android intents and MposSDK
 *
 * @author lmenu@eliberty.fr
 *
 */
public class CordovaPayzen extends CordovaPlugin
{
    private Activity activity;
    private static final String START_ACTIVITY = "startActivity";
    private static final String TOUCH_INIT_MPOS_IN_ERROR = "TOUCH_INIT_MPOS_IN_ERROR";
    private static final String TOUCH_CARD_READER_NOT_AVAILABLE = "TOUCH_CARD_READER_NOT_AVAILABLE";
    private static final String TOUCH_SDK_NOT_READY = "TOUCH_SDK_NOT_READY";
    private CallbackContext callbackContext = null;
    private String token;
    private String acceptorId;
    private String label;
    private String email;
    private Long amount;
    private String orderId;
    private Boolean testMode;
    private static final String PAYMENT_MODE_PRODUCTION = "PRODUCTION";
    private static final String PAYMENT_MODE_TEST = "TEST";
    private static Raven raven;

    /**
     * Method witch permit to initialize the Cordova Payzen Plugin
     */
    @Override
    protected void pluginInitialize()
    {
        activity = this.cordova.getActivity();
        Application application = activity.getApplication();
        Context context = activity.getApplicationContext();

        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(activity.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            String dsn = bundle.getString("com.eliberty.touchv2.sentry.android.DSN");
            LOG.w("eliberty.cordova.plugin.payzen", "**** Eliberty dsn : **** " + dsn + " package name " + activity.getPackageName());

            raven = RavenFactory.ravenInstance(dsn);
            LOG.i("eliberty.cordova.plugin.payzen", "pluginInitialize");
            MposSDK.init(application);
            MposSDK.setThemeColor(Color.parseColor("#F98253"));
        }
        catch (MposException e) {
            raven.sendException(e);
            LOG.w("eliberty.cordova.plugin.payzen", "TOUCH_INIT_MPOS_IN_ERROR");
            runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, TOUCH_INIT_MPOS_IN_ERROR);
        }
        catch (PackageManager.NameNotFoundException nnfe) {
            LOG.w("eliberty.cordova.plugin.payzen", "TOUCH_INIT_MPOS_IN_ERROR");
            runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, TOUCH_INIT_MPOS_IN_ERROR);
        }
    }

    /**
     * Executes the request.
     * https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java
     *
     * This method is called from the WebView thread. To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return                Whether the action was valid.
     *
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
    {
        LOG.i("eliberty.cordova.plugin.payzen", "execute Cordova");
        this.callbackContext = callbackContext;
        final JSONArray finalArgs = args;

        if (action.equals(START_ACTIVITY)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        JSONObject obj = finalArgs.getJSONObject(0);
                        token = obj.has("token") ? obj.getString("token") : null;
                        acceptorId = obj.has("acceptorId") ? obj.getString("acceptorId") : null;
                        label = obj.has("label") ? obj.getString("label") : null;
                        email = obj.has("email") ? obj.getString("email") : null;
                        amount = Long.parseLong(obj.has("amount") ? obj.getString("amount") : "0");
                        orderId = obj.has("orderId") ? obj.getString("orderId") : null;
                        testMode = obj.has("testMode") && Boolean.parseBoolean(obj.getString("testMode"));

                        startActivity(0);
                    }
                    catch (JSONException ex) {
                        raven.sendException(ex);
                        LOG.w("eliberty.cordova.plugin.payzen", "JSONException: " + ex.getMessage());
                    }
                }
            });
        }

        return true;
    }


    /**
     * Register token and start the mpos activity
     *
     * @param nbAttempts Number of attempts. When attempts is 10 we launch callback error
     */
    private void startActivity(Integer nbAttempts)
    {
        try {
            LOG.i("eliberty.cordova.plugin.payzen", "registerToken");
            MposSDK.registerToken(this.token);

            if (MposSDK.isCardReaderAvailable()) {
                LOG.i("eliberty.cordova.plugin.payzen", "isCardReaderAvailable");
                MposResult mposResult = MposSDK.start(activity, this.acceptorId);
                LOG.i("eliberty.cordova.plugin.payzen", "start SDK");
                mposResult.setCallback(new MposResult.ResultCallback() {
                    @Override
                    public void onSuccess(Result result) {
                        launchSuccessStartActivity();
                    }

                    @Override
                    public void onError(Result error) {
                        runCallbackError(error.getCode(), error.getMessage());
                    }

                    @Override
                    public void onError(Throwable e) {
                        runCallbackError(Integer.toString(e.hashCode()), e.getMessage());
                    }
                });
            } else if (nbAttempts < 10) {
                LOG.i("eliberty.cordova.plugin.payzen", "not ready start SDK");
                Thread.sleep(5000);
                nbAttempts++;
                startActivity(nbAttempts);
            } else {
                runCallbackError(TOUCH_CARD_READER_NOT_AVAILABLE, TOUCH_CARD_READER_NOT_AVAILABLE);
            }
        }
        catch (MposException e) {
            raven.sendException(e);
            runCallbackError(e.getTypeException(), e.getMessage());
        }
        catch(InterruptedException ex) {
            raven.sendException(ex);
            runCallbackError(Integer.toString(ex.hashCode()), ex.getMessage());
        }
    }

    /**
     *  Prepare the mTransaction object and execute transaction is SDK is ready
     */
    private void launchSuccessStartActivity()
    {
        LOG.i("eliberty.cordova.plugin.payzen", "launchSuccessStartActivity");
        MCurrency currency = MposSDK.getDefaultCurrencies();

        MCustomer mposCustomer = new MCustomer();
        mposCustomer.setEmail(email);

        MTransaction mTransaction = new MTransaction();
        mTransaction.setAmount(amount);
        mTransaction.setCurrency(currency);
        mTransaction.setOrderId(orderId);
        mTransaction.setOperationType(MTransactionType.DEBIT);
        mTransaction.setCustomer(mposCustomer);
        mTransaction.setOrderInfo(label);

        if (MposSDK.isReady()) {
            LOG.i("eliberty.cordova.plugin.payzen", "SDK is ready");
            executeTransaction(mTransaction);
        } else {
            LOG.i("eliberty.cordova.plugin.payzen", "SDK is not ready !");
            runCallbackError(TOUCH_SDK_NOT_READY, TOUCH_SDK_NOT_READY);
        }
    }

    /**
     * Execute the transaction with the mpos
     *
     * @param mTransaction The object transaction
     */
    private void executeTransaction(MTransaction mTransaction)
    {
        try {
            LOG.i("eliberty.cordova.plugin.payzen", "executeTransaction");
            String mode = testMode ?  PAYMENT_MODE_TEST : PAYMENT_MODE_PRODUCTION;
            MposResult mposResult = MposSDK.executeTransaction(activity, mTransaction, false, mode);

            mposResult.setCallback(new MposResult.ResultCallback() {
                @Override
                public void onSuccess(Result result) {
                    runCallbackSuccess(result);
                }
                @Override
                public void onError(Result error) {
                    runCallbackError(error.getCode(), error.getMessage());
                }
                @Override
                public void onError(Throwable e) {
                    runCallbackError(Integer.toString(e.hashCode()), e.getMessage());
                }
            });
        }
        catch (MposException e) {
            raven.sendException(e);
            LOG.w("eliberty.cordova.plugin.payzen", "MposException : " + e.getMessage());
            runCallbackError(e.getTypeException(), e.getMessage());
        }
    }

    /**
     * Return a Json object for the cordova's callback in case of mpos success
     * @param result The result of Transaction
     */
    private void runCallbackSuccess(Result result)
    {
        try {
            LOG.i("eliberty.cordova.plugin.payzen", "call success callback runCallbackSuccess");
            JSONObject obj = new JSONObject();
            obj.put("transactionId", result.getTransaction().getTransactionId());
            obj.put("transactionUuId", result.getTransaction().getTransactionUuid());
            obj.put("status", result.getTransaction().getTransactionStatusLabel().toString());
            obj.put("receipt", result.getTransaction().getReceipt());
            obj.put("transactionDate", result.getTransaction().getSubmissionDate());
            callbackContext.success(obj);
        }
        catch (JSONException jse) {
            raven.sendException(jse);
            LOG.w("eliberty.cordova.plugin.payzen", "JSONException : " + jse.getMessage());
            runCallbackError(Integer.toString(jse.hashCode()), jse.getMessage());
        }
    }

    /**
     * Return a Json object for the cordova's callback in case of mpos error
     *
     * @param code The code error
     * @param message The message error
     */
    private void runCallbackError(String code, String message)
    {
        try {
            LOG.i("eliberty.cordova.plugin.payzen", "call error callback runCallbackError");
            JSONObject obj = new JSONObject();
            obj.put("code", code);
            obj.put("message", message);
            callbackContext.error(obj);
        }
        catch (JSONException jse) {
            raven.sendException(jse);
            LOG.w("eliberty.cordova.plugin.payzen", "JSONException : " + jse.getMessage());
            runCallbackError(Integer.toString(jse.hashCode()), jse.getMessage());
        }
    }

    /**
     * On destroy, we must remove all callback and destroy app
     */
    @Override
    public void onDestroy()
    {
        LOG.i("eliberty.cordova.plugin.payzen", "shutdown MposSDK");
        try {
            MposSDK.shutdown();
        }
        catch (MposException e) {
            raven.sendException(e);
            LOG.w("eliberty.cordova.plugin.payzen", "MposException : " + e.getMessage());
            runCallbackError(e.getTypeException(), e.getMessage());
        }
    }
}
