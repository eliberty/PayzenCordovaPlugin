# Plugin Cordova for MPOS Payzen #
==================================

Ludovic Menu @Eliberty Services SAS

INSTALL :
---------

cordova plugin add https://github.com/lmeliberty/payzenSdk


HOW TO USE IN ES6 :
-------------------

```javascript
const getPayzenToken = () => {
	// @Todo Implement create a token identification
};

const notifyFunction = (error, paymentInError = false) => {  
  if (paymentInError === true) {
    setTimeout(() => { /* @Todo update payment in error */ }, 2000);    
  } else {
    setTimeout(() => { /* @Todo Payment is aborted, we can try a new payment */ }, 2000);
  }
};

const successFunction = (data) => { 
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
      notifyFunction(data, true);
      break;
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

window.plugins.CordovaPayzen.startActivity(params, successFunction, notifyFunction);
```