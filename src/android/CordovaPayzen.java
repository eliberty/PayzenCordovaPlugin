package com.eliberty.cordova.plugin.payzen;


import android.graphics.Color;
import com.lyranetwork.mpos.sdk.InitDongleCallback;
import com.lyranetwork.mpos.sdk.MCurrency;
import com.lyranetwork.mpos.sdk.MCustomer;
import com.lyranetwork.mpos.sdk.MTransaction;
import com.lyranetwork.mpos.sdk.MTransactionType;
import com.lyranetwork.mpos.sdk.MposResult;
import com.lyranetwork.mpos.sdk.MposSDK;
import com.lyranetwork.mpos.sdk.MposSdkUtil;
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
    private static final String SENTRY_DSN = "XXXXX";
    private static final String START_ACTIVITY = "startActivity";
    private static final String TOUCH_INIT_MPOS_IN_ERROR = "TOUCH_INIT_MPOS_IN_ERROR";
    private static final String TOUCH_SDK_NOT_READY = "TOUCH_SDK_NOT_READY";
    private static final String TOUCH_TRANSACTION_MPOS_IN_ERROR = "TOUCH_TRANSACTION_MPOS_IN_ERROR";
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
        // Init Sentry
        if (raven == null) {
            raven = RavenFactory.ravenInstance(SENTRY_DSN);
        }

        LOG.w("eliberty.cordova.plugin.payzen", "execute Cordova");
        this.callbackContext = callbackContext;
        final JSONArray finalArgs = args;

        if (action.equals(START_ACTIVITY)) {
            cordova.getActivity().runOnUiThread(new Runnable() {
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

                    // Init SDK must done only once
                    if(!MposSdkUtil.isSdkInitialized()) {
                        LOG.w("eliberty.cordova.plugin.payzen", "registerToken " + token);
                        MposSDK.registerToken(token);
                        LOG.w("eliberty.cordova.plugin.payzen", "execute initDongle : " + acceptorId);
                        MposSDK.initDongle(acceptorId, cordova.getActivity(), initDongleCallback());
                    }

                }
                catch (JSONException ex) {
                    raven.sendException(ex);
                    LOG.w("eliberty.cordova.plugin.payzen", "JSONException: " + ex.getMessage());
                }
                catch (MposException e) {
                    raven.sendException(e);
                    //Init failed
                    LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.init() fail", e);
                    runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, e.getMessage());
                }
                catch (Exception e) {
                    raven.sendException(e);
                    LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.init() fail", e);
                    runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, e.getMessage());
                }
                }
            });
        }

        return true;
    }

    /**
     * Init the dongle Callback
     *
     * @return InitDongleCallback
     */
    private InitDongleCallback initDongleCallback() {
        return (new InitDongleCallback() {
            @Override
            public void onInitDongleSuccess(String s, MCurrency[] mCurrencies)
            {
                // SDK and CardReader are ready to make payments
                // Store currencies to execute future transactions
                MposSDK.setThemeColor(Color.parseColor("#F98253"));
                launchSuccessStartActivity();
            }
            @Override
            public void onInitDongleError(Result result) {
                // Error message is stored on result.getMessage()
                // Don’t need to call initDongle() another time, SDK will manage automatically next retry
                LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.init() fail", result);
                runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, result.getMessage());

            }
            @Override
            public void onInitDongleError(Throwable throwable) {
                // Error message is stored on throwable.getMessage()
                // Don’t need to call initDongle() another time, SDK will manage automatically next retry
                raven.sendException(throwable);
                LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.init() fail", throwable);
                runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, throwable.getMessage());
            }
        });
    }

    /**
     *  Prepare the mTransaction object and execute transaction if SDK is ready
     */
    private void launchSuccessStartActivity()
    {
        LOG.w("eliberty.cordova.plugin.payzen", "launchSuccessStartActivity");
        MCurrency currency = MposSDK.getDefaultCurrencies();

        if(currency!=null) {
            MCustomer mposCustomer = new MCustomer();
            mposCustomer.setEmail(email);

            MTransaction mTransaction = new MTransaction();
            mTransaction.setAmount(amount);
            mTransaction.setCurrency(currency);
            mTransaction.setOrderId(orderId);
            mTransaction.setOperationType(MTransactionType.DEBIT);
            mTransaction.setCustomer(mposCustomer);
            mTransaction.setOrderInfo(label);

            executeTransaction(mTransaction);
        }else{
            LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.getDefaultCurrencies() is null");
            raven.sendMessage("MposSDK.getDefaultCurrencies() is null");
            runCallbackError(TOUCH_SDK_NOT_READY, "MposSDK.getDefaultCurrencies() is null");
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
            LOG.w("eliberty.cordova.plugin.payzen", "executeTransaction");

            String mode = testMode ?  PAYMENT_MODE_TEST : PAYMENT_MODE_PRODUCTION;

            MposResult mposResult = MposSDK.executeTransaction(this.cordova.getActivity(), mTransaction, false, mode);
            if(mposResult!=null) {
                mposResult.setCallback(new MposResult.ResultCallback() {
                    @Override
                    public void onSuccess(Result result) {
                        LOG.w("eliberty.cordova.plugin.payzen", "MposResult.ResultCallback success");
                        runCallbackSuccess(result);
                    }

                    @Override
                    public void onError(Result error) {
                        LOG.w("eliberty.cordova.plugin.payzen", "MposResult.ResultCallback on error");
                        runCallbackError(error.getCode(), error.getMessage());
                    }

                    @Override
                    public void onError(Throwable e) {
                        LOG.w("eliberty.cordova.plugin.payzen", "MposResult.ResultCallback on error Throwable");
                        runCallbackError(Integer.toString(e.hashCode()), e.getMessage());
                    }
                });
            } else {
                raven.sendMessage("MposSDK.executeTransaction() return null");
                LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.executeTransaction() return null");
                runCallbackError(TOUCH_TRANSACTION_MPOS_IN_ERROR, "MposSDK.executeTransaction() return null");
            }
        }
        catch (Exception ex) {
            LOG.w("eliberty.cordova.plugin.payzen", "Exception", ex);
            raven.sendException(ex);
            runCallbackError(Integer.toString(ex.hashCode()), ex.getMessage());
        }
    }

    /**
     * Return a Json object for the cordova's callback in case of mpos success
     * @param result The result of Transaction
     */
    private void runCallbackSuccess(Result result)
    {
        try {
            LOG.w("eliberty.cordova.plugin.payzen", "call success callback runCallbackSuccess");
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
        catch (Exception ex) {
            LOG.w("eliberty.cordova.plugin.payzen", "Exception", ex);
            raven.sendException(ex);
            runCallbackError(Integer.toString(ex.hashCode()), ex.getMessage());
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
            LOG.w("eliberty.cordova.plugin.payzen", "call error callback runCallbackError");
            JSONObject obj = new JSONObject();
            obj.put("code", code);
            obj.put("message", message);

            synchronized(callbackContext){
                callbackContext.error(obj);
                callbackContext.notify();
            }
        }
        catch (JSONException jse) {
            raven.sendException(jse);
            LOG.w("eliberty.cordova.plugin.payzen", "JSONException : " + jse.getMessage());
        }
        catch (Exception ex) {
            LOG.w("eliberty.cordova.plugin.payzen", "Exception", ex);
            raven.sendException(ex);
        }
    }
}