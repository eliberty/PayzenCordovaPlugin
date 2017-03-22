# Plugin Cordova for MPOS Payzen #
==================================

Ludovic Menu @Eliberty Services SAS

INSTALL :
---------

cordova plugin add https://github.com/eliberty/PayzenCordovaPlugin


HOW TO USE IN ES6 :
-------------------

```javascript
const getPayzenToken = () => {
	// @Todo Implement create a token identification
};


const notifyFunction = (error, paymentInError = false) => {
  // display message error.code

  if (paymentInError === true) {
    /* @Todo update payment in error */
  } else {
    /* @Todo Payment is aborted, suggest a new payment */
  }
};

const params = {
  token: getPayzenToken() // token,
  acceptorId: XXXXXXXXXX // Acceptor ID,
  transactionId: XXXXXXXXXX //Transaction ID,
  label: XXXXXXXXXX // Label,
  email: XXXXXXXXXX // Email,
  amount: XXXXXXXXXX // Amount,
  orderId: XXXXXXXXXX // OrderId,
  testMode: XXXXXXXXXX // Enable test mode or not,
};

const failedCallbackFunction = (data) => {
  //Log failedCallbackfunction and launch notifyFunction
  notifyFunction(data, false);
};

const successCallbackFunction = (data) => {  
  switch (data.status) {
    case 'ABORTED':      
      notifyFunction({ code: 'CORDOVA_PAYZEN_ABORTED' }, false);
      break;
    case 'APPROVED':      
       // @Todo Payment is approved, we can update payment and execute the next instructions
      break;
    case 'DECLINED':      
      notifyFunction({ code: 'CORDOVA_PAYZEN_DECLINED' }, true);
      break;
    default:
      notifyFunction(null, true);
      break;
  }
};

window.plugins.CordovaPayzen.startActivity(
  params,
  successCallbackFunction,
  failedCallbackFunction,
);
```