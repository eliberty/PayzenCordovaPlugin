package com.eliberty.cordova.plugin.payzen;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
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
    private static final String SENTRY_DSN = "XXXXXX";
    private Activity activity;
    private static final String START_ACTIVITY = "startActivity";
    private static final String TOUCH_INIT_MPOS_IN_ERROR = "TOUCH_INIT_MPOS_IN_ERROR";
    private static final String TOUCH_CARD_READER_NOT_AVAILABLE = "TOUCH_CARD_READER_NOT_AVAILABLE";
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
     * Method witch permit to initialize the Cordova Payzen Plugin
     */
    @Override
    protected void pluginInitialize()
    {
        activity = this.cordova.getActivity();
        Application application = activity.getApplication();

        // Init Sentry
        raven = RavenFactory.ravenInstance(SENTRY_DSN);

        LOG.i("eliberty.cordova.plugin.payzen", "pluginInitialize");

        // Init SDK must done only once
        if(!MposSDK.isSdkInitialized()) {
            try {
                MposSDK.init(application);
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
            MposSDK.setThemeColor(Color.parseColor("#F98253"));
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
        // Init Sentry
        if (raven == null) {
            raven = RavenFactory.ravenInstance(SENTRY_DSN);
        }

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

                        if(!MposSDK.isSdkInitialized()) {
                            raven.sendMessage("MposSDK not initialized, cannot start() mPOS SDK");
                            runCallbackError(TOUCH_CARD_READER_NOT_AVAILABLE, "MposSDK not initialized, check MposException returned by the init() method");
                        }else {
                            startActivity(0);
                        }
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
                // @todo: ne pas relancer peut etre le start à chaque paiement ?
                MposResult mposResult = MposSDK.start(activity, this.acceptorId);
                if (mposResult != null) {
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
                } else {
                    LOG.w(TOUCH_INIT_MPOS_IN_ERROR, "MposSDK start() return null. MposSDK not initialized, check MposException returned by the init() method");
                    runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, "MposSDK start() return null. MposSDK not initialized, check MposException returned by the init() method");
                }
            } else if (nbAttempts < 10) {
                LOG.i("eliberty.cordova.plugin.payzen", "not ready start SDK");
                Thread.sleep(5000);
                nbAttempts++;
                startActivity(nbAttempts);
            } else {
                raven.sendMessage("CardReader not responding after 50 seconds. Is dongle turned on?");
                runCallbackError(TOUCH_CARD_READER_NOT_AVAILABLE, "CardReader not responding. Is dongle turned on?");
            }
        } catch (MposException e) {
            LOG.w("eliberty.cordova.plugin.payzen", "Error on starting mPOS SDK: MposException", e);
            raven.sendException(e);
            runCallbackError(e.getTypeException(), e.getMessage());
        } catch (InterruptedException ex) {
            LOG.w("eliberty.cordova.plugin.payzen", "Error on starting mPOS SDK: InterruptedException", ex);
            raven.sendException(ex);
            runCallbackError(Integer.toString(ex.hashCode()), ex.getMessage());
        } catch (Exception ex) {
            LOG.w("eliberty.cordova.plugin.payzen", "Error on starting mPOS SDK: Exception", ex);
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

        if (currency!=null) {
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
                LOG.w("eliberty.cordova.plugin.payzen", "SDK is not ready !");
                runCallbackError(TOUCH_SDK_NOT_READY, TOUCH_SDK_NOT_READY);
            }
        } else {
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
            LOG.i("eliberty.cordova.plugin.payzen", "executeTransaction");

            String mode = testMode ?  PAYMENT_MODE_TEST : PAYMENT_MODE_PRODUCTION;

            MposResult mposResult = MposSDK.executeTransaction(activity, mTransaction, false, mode);
            if (mposResult!=null) {
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
            } else {
                raven.sendMessage("MposSDK.executeTransaction() return null");
                LOG.w("eliberty.cordova.plugin.payzen", "MposSDK.executeTransaction() return null");
                runCallbackError(TOUCH_TRANSACTION_MPOS_IN_ERROR, "MposSDK.executeTransaction() return null");
            }
        }
        catch (MposException e) {
            raven.sendException(e);
            LOG.w("eliberty.cordova.plugin.payzen", "MposException : " + e.getMessage(), e);
            runCallbackError(e.getTypeException(), e.getMessage());
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
            LOG.i("eliberty.cordova.plugin.payzen", "call error callback runCallbackError");
            JSONObject obj = new JSONObject();
            obj.put("code", code);
            obj.put("message", message);
            callbackContext.error(obj);
            callbackContext.notify();
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