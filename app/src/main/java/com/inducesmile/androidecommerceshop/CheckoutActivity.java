package com.inducesmile.androidecommerceshop;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inducesmile.androidecommerceshop.adapter.CheckoutAdapter;
import com.inducesmile.androidecommerceshop.entity.CartObject;
import com.inducesmile.androidecommerceshop.entity.CheckoutObject;
import com.inducesmile.androidecommerceshop.entity.PaymentResponseObject;
import com.inducesmile.androidecommerceshop.entity.SuccessObject;
import com.inducesmile.androidecommerceshop.entity.UserObject;
import com.inducesmile.androidecommerceshop.network.GsonRequest;
import com.inducesmile.androidecommerceshop.network.VolleySingleton;
import com.inducesmile.androidecommerceshop.utils.CustomApplication;
import com.inducesmile.androidecommerceshop.utils.CustomSharedPreference;
import com.inducesmile.androidecommerceshop.utils.Helper;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;

import org.json.JSONException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.inducesmile.androidecommerceshop.R.string.subtotal;
import static com.inducesmile.androidecommerceshop.utils.Helper.MY_SOCKET_TIMEOUT_MS;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = CheckoutActivity.class.getSimpleName();

    private TextView orderItemCount, orderTotalAmount, orderVat, orderFullAmount, orderDiscount, orderShipping;
    private TextView restaurantName, restaurantAddress;

    private ImageView editAddress;

    private CustomSharedPreference shared;

    private RadioButton mainAdress, secondaryAddress;
    private RadioGroup addressGroup;
    private String alternativeAddress;
    private String selectedAddress = "";

    private RadioGroup radioGroup;
    private RadioButton payPalPayment;
    private RadioButton creditCardPayment;
    private RadioButton cashOnDelivery;

    private UserObject user;
    private List<CartObject> checkoutOrder;
    private String finalList;

    private double subTotal;
    private double finalCost;

    private Gson gson;

    private CheckoutObject checkoutObject;
    private float taxPercent;

    private static PayPalConfiguration config;
    private static final int REQUEST_PAYMENT_CODE = 99;

    private boolean isDiscount = false;
    private String[] couponDiscountType;
    private int shippingCost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        config = new PayPalConfiguration().environment(PayPalConfiguration.ENVIRONMENT_NO_NETWORK).clientId(Helper.PAY_PAL_CLIENT_ID);

        if(!Helper.isNetworkAvailable(this)){
            Helper.displayErrorMessage(this, getString(R.string.no_internet));
        }else{
            checkoutInformationFromRemoteServer();
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setTitle(getString(R.string.checkouts));

        isDiscount = getIntent().getExtras().getBoolean("REDEEM");

        finalList = getIntent().getExtras().getString("FINAL_ORDER");
        Log.d(TAG, "JSON FORMAT" + finalList);
        gson = ((CustomApplication)getApplication()).getGsonObject();
        CartObject[] mOrders = gson.fromJson(finalList, CartObject[].class);
        checkoutOrder = ((CustomApplication)getApplication()).convertObjectArrayToListObject(mOrders);

        couponDiscountType = getProductDiscount(checkoutOrder);

        shared = ((CustomApplication)getApplication()).getShared();
        user = gson.fromJson(shared.getUserData(), UserObject.class);

        radioGroup = (RadioGroup)findViewById(R.id.radio_group);
        payPalPayment = (RadioButton)findViewById(R.id.pay_pal_payment);
        creditCardPayment = (RadioButton)findViewById(R.id.credit_card_payment);
        cashOnDelivery = (RadioButton)findViewById(R.id.cash_on_delivery);

        editAddress = (ImageView)findViewById(R.id.add_address);

        addressGroup = (RadioGroup)findViewById(R.id.address_group);
        //selectDeliveryAddress();
        mainAdress = (RadioButton)findViewById(R.id.main_address);
        secondaryAddress = (RadioButton)findViewById(R.id.secondary_address);

        restaurantName = (TextView)findViewById(R.id.restaurant_name);
        restaurantAddress = (TextView)findViewById(R.id.restaurant_address);

        alternativeAddress = ((CustomApplication)getApplication()).getShared().getSavedDeliveryAddress();
        if(TextUtils.isEmpty(alternativeAddress)){
            secondaryAddress.setVisibility(View.GONE);
            mainAdress.setText(user.getAddress());
        }else{
            secondaryAddress.setText(alternativeAddress);
            mainAdress.setText(user.getAddress());
        }

        orderItemCount = (TextView)findViewById(R.id.order_item_count);
        orderTotalAmount =(TextView)findViewById(R.id.order_total_amount);
        orderVat = (TextView)findViewById(R.id.order_vat);
        orderFullAmount = (TextView)findViewById(R.id.order_full_amounts);

        orderDiscount = (TextView)findViewById(R.id.order_discount);
        if(isDiscount){
            if(couponDiscountType[0].equals("Percentage")){
                orderDiscount.setText(couponDiscountType[1] + "%");
            }else{
                orderDiscount.setText("$" + couponDiscountType[1]);
            }
        }
        orderShipping = (TextView)findViewById(R.id.order_shipping);

        orderItemCount.setText(String.valueOf(checkoutOrder.size()));
        subTotal = ((CustomApplication)getApplication()).getSubtotalAmount(checkoutOrder);
        orderTotalAmount.setText("$" + String.valueOf(subTotal) + "0");

        RecyclerView checkoutRecyclerView = (RecyclerView)findViewById(R.id.checkout_item);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        checkoutRecyclerView.setLayoutManager(linearLayoutManager);

        checkoutRecyclerView.setHasFixedSize(true);

        CheckoutAdapter mAdapter = new CheckoutAdapter(CheckoutActivity.this, checkoutOrder);
        checkoutRecyclerView.setAdapter(mAdapter);

        editAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent newAddressIntent = new Intent(CheckoutActivity.this, NewAddressActivity.class);
                newAddressIntent.putExtra("PRIMARY_ADDRESS", user.getAddress());
                startActivity(newAddressIntent);
            }
        });

        Button placeOrderButton = (Button)findViewById(R.id.place_an_order);
        placeOrderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(radioGroup.getCheckedRadioButtonId() < 0){
                    Helper.displayErrorMessage(CheckoutActivity.this, "Payment option must be selected before checkout");
                    return;
                }

                if(addressGroup.getCheckedRadioButtonId() < 0){
                    Helper.displayErrorMessage(CheckoutActivity.this, "You must provide a delivery address before checkout");
                    return;
                }
                String sAddress = returnAddress();
                Log.d(TAG, "Address: " + sAddress);

                String paymentSelected = getSelectedPayment();
                Log.d(TAG, "Payment: " + paymentSelected);

                int productDiscount = 0;
                if(isDiscount){
                    productDiscount = Integer.parseInt(couponDiscountType[1]);
                }

                if(paymentSelected.equals("PAY PAL")){
                    initializePayPalPayment();
                    Log.d(TAG, "Still inside");
                }else if(paymentSelected.equals("CREDIT CARD")){
                    String serverContent = user.getId() + ";" + checkoutOrder.size() + ";" + productDiscount + ";" + taxPercent +
                            ";" + finalCost + ";" + sAddress + ";" + shippingCost + ";" + paymentSelected + ";" + finalList;
                    Intent cardIntent = new Intent(CheckoutActivity.this, CreditCardActivity.class);
                    Log.d(TAG, "Server Content " + serverContent);
                    cardIntent.putExtra("STRIPE_PAYMENT", serverContent);
                    startActivity(cardIntent);
                }else if(paymentSelected.equals("CASH ON DELIVERY")){
                    //postCheckoutOrderToRemoteServer(String.valueOf(user.getId()), String.valueOf(checkoutOrder.size()), String.valueOf(productDiscount),
                            //String.valueOf(taxPercent), String.valueOf(finalCost), sAddress, String.valueOf(shippingCost), paymentSelected, finalList);
                }
            }
        });

    }

    private void checkoutInformationFromRemoteServer(){
        GsonRequest<CheckoutObject> serverRequest = new GsonRequest<CheckoutObject>(
                Request.Method.GET,
                Helper.PATH_TO_CHECKOUT,
                CheckoutObject.class,
                createRequestSuccessListener(),
                createRequestErrorListener());

        serverRequest.setRetryPolicy(new DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(this).addToRequestQueue(serverRequest);
    }

    private Response.Listener<CheckoutObject> createRequestSuccessListener() {
        return new Response.Listener<CheckoutObject>() {
            @Override
            public void onResponse(CheckoutObject response) {
                try {
                    Log.d(TAG, "Json Response " + response.toString());
                    if(!TextUtils.isEmpty(response.getCompany_name()) || !TextUtils.isEmpty(response.getCompany_address())){
                        checkoutObject = response;
                        restaurantName.setText(response.getCompany_name());
                        restaurantAddress.setText(response.getCompany_address());

                        shippingCost = response.getSettings_shipping();
                        taxPercent = response.getSettings_tax();

                        orderShipping.setText("$" + shippingCost);

                        finalCost = getFinalTotalPrice(shippingCost, taxPercent);

                        orderVat.setText(String.valueOf(taxPercent) + "%");
                        orderFullAmount.setText("$" + String.valueOf(finalCost));

                    }else{
                        Helper.displayErrorMessage(CheckoutActivity.this, "Shop information missing in the server! please contact admin");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private Response.ErrorListener createRequestErrorListener() {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        };
    }


    private double getFinalTotalPrice(int shipping, float tax){
        if(isDiscount){
            int discount = Integer.parseInt(couponDiscountType[1]);
            subTotal = subTotal - discount;
        }

        int shippingAmount = 0;
        double discountTotal = 0;
        if(shipping != 0){
            shippingAmount = shipping;
        }
        discountTotal = subTotal + shippingAmount;
        Log.d(TAG, "subtotal " + subtotal);
        Log.d(TAG, "discount " + discountTotal);
        int taxAmount = 0;
        if(tax != 0){
            taxAmount = (int)((tax * discountTotal)/100);
        }
        double finalTotal = discountTotal + taxAmount;
        return finalTotal;
    }

    private String returnAddress(){
        String chosenAddress = "";
        if(mainAdress.isChecked()){
            chosenAddress = user.getAddress();
        }
        if(secondaryAddress.isChecked()){
            chosenAddress = alternativeAddress;
        }
        return chosenAddress;
    }

    private String getSelectedPayment(){
        String payment = "";
        if(payPalPayment.isChecked()){
            payment = "PAY PAL";
        }
        if(creditCardPayment.isChecked()){
            payment= "CREDIT CARD";
        }
        if(cashOnDelivery.isChecked()){
            payment = "CASH ON DELIVERY";
        }
        return payment;
    }

    private void initializePayPalPayment(){
        PayPalPayment payment = new PayPalPayment(new BigDecimal(String.valueOf(subTotal)), "USD", "Product Order", PayPalPayment.PAYMENT_INTENT_SALE);
        Intent intent = new Intent(this, PaymentActivity.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payment);
        startActivityForResult(intent, REQUEST_PAYMENT_CODE);
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_PAYMENT_CODE) {
            PaymentConfirmation confirm = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
            if (confirm != null) {
                try {
                    String jsonPaymentResponse = confirm.toJSONObject().toString(4);
                    GsonBuilder builder = new GsonBuilder();
                    Gson gson = builder.create();
                    PaymentResponseObject responseObject = gson.fromJson(jsonPaymentResponse, PaymentResponseObject.class);
                    if(responseObject != null){
                        String paymentId = responseObject.getResponse().getId();
                        String paymentState = responseObject.getResponse().getState();
                        Log.d(TAG, "Log payment id and state " + paymentId + " " + paymentState);

                        int productDiscount = 0;
                        if(isDiscount){
                            productDiscount = Integer.parseInt(couponDiscountType[1]);
                        }
                        //send order to server
                        String deliveryAddress = returnAddress();
                        postCheckoutOrderToRemoteServer(String.valueOf(user.getId()), String.valueOf(checkoutOrder.size()), String.valueOf(productDiscount),
                                String.valueOf(taxPercent), String.valueOf(finalCost), deliveryAddress, String.valueOf(shippingCost), "PAY PAL", finalList);

                    }
                } catch (JSONException e) {
                    Log.e("paymentExample", "an extremely unlikely failure occurred: ", e);
                }
            }
        }
        else if (resultCode == Activity.RESULT_CANCELED) {
            Log.i("paymentExample", "The user canceled.");
        }
        else if (resultCode == PaymentActivity.RESULT_EXTRAS_INVALID) {
            Log.i("paymentExample", "An invalid Payment or PayPalConfiguration was submitted. Please see the docs.");
        }
    }


    private void postCheckoutOrderToRemoteServer(String userId, String quantity, String discount, String tax, String price,
                                                 String address, String shipping, String payment_method, String order_list){
        Map<String, String> params = new HashMap<String,String>();
        params.put("USER_ID", userId);
        params.put("QUANTITY", quantity);
        params.put("DISCOUNT", discount);
        params.put("TAX", tax);
        params.put("TOTAL_PRICE", price);
        params.put("ADDRESS", address);
        params.put("SHIPPING", shipping);
        params.put("PAYMENT", payment_method);
        params.put("ORDER_LIST", order_list);

        GsonRequest<SuccessObject> serverRequest = new GsonRequest<SuccessObject>(
                Request.Method.POST,
                Helper.PATH_TO_PLACE_ORDER,
                SuccessObject.class,
                params,
                createOrderRequestSuccessListener(),
                createOrderRequestErrorListener());

        serverRequest.setRetryPolicy(new DefaultRetryPolicy(
                MY_SOCKET_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        VolleySingleton.getInstance(CheckoutActivity.this).addToRequestQueue(serverRequest);
    }

    private Response.Listener<SuccessObject> createOrderRequestSuccessListener() {
        return new Response.Listener<SuccessObject>() {
            @Override
            public void onResponse(SuccessObject response) {
                try {
                    Log.d(TAG, "Json Response " + response.getSuccess());
                    if(response.getSuccess() == 1){
                        //delete paid other
                        ((CustomApplication)getApplication()).getShared().updateCartItems("");
                        //confirmation page.
                        Intent orderIntent = new Intent(CheckoutActivity.this, ComfirmationActivity.class);
                        startActivity(orderIntent);
                    }else{
                        Helper.displayErrorMessage(CheckoutActivity.this, "There is an issue why placing your order. Please try again");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private Response.ErrorListener createOrderRequestErrorListener() {
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        };
    }

    private String[] getProductDiscount(List<CartObject> productsInCart){
        String[] cType = new String[2];
        for(int i = 0; i < productsInCart.size(); i++){
            CartObject productObject = productsInCart.get(i);
            if(!TextUtils.isEmpty(productObject.getCouponType())){
                String discountType = productObject.getCouponType();
                int discountValue = productObject.getDiscount();
                cType[0] = discountType;
                cType[1] = String.valueOf(discountValue);
                break;
            }
        }
        return cType;
    }

    @Override
    public void onDestroy() {
        stopService(new Intent(this, PayPalService.class));
        super.onDestroy();
    }

}
