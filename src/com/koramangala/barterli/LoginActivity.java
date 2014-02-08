package com.koramangala.barterli;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.RequestToken;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import com.facebook.*;
import com.facebook.model.*;
import com.facebook.widget.LoginButton;
import com.facebook.widget.LoginButton.OnErrorListener;


import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class LoginActivity extends Activity {
	
	// Twitter
	private static Twitter twitter;
	private static RequestToken requestToken;
	// Shared Preferences
	private static SharedPreferences mSharedPreferences;
	// Internet Connection detector
	private ConnectionDetector connection_status_detector;
	// Alert Dialog Manager
	private AlertDialogManager alert = new AlertDialogManager();
	private TextView welcome;
	private Editor sharedPrefEditor;
	List<String> permissions;
	Bundle parameters;
	private ProgressDialogManager myProgressDialogManager = new ProgressDialogManager();
	String registration_url; 
	String Auth_Token;
	
	
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        setContentView(R.layout.login_activity);  
        mSharedPreferences = getApplicationContext().getSharedPreferences("BarterLiPref", 0);

        connection_status_detector = new ConnectionDetector(getApplicationContext());
        sharedPrefEditor = mSharedPreferences.edit();
        permissions = new ArrayList<String>();
        permissions.add("email");
        LoginButton authButton = (LoginButton) findViewById(R.id.authButton);
        registration_url = getResources().getString(R.string.create_account);
        
        if(mSharedPreferences.contains(AllConstants.PREF_BARTER_LI_AUTHO_TOKEN) && mSharedPreferences!=null){
        	Auth_Token = mSharedPreferences.getString(AllConstants.PREF_BARTER_LI_AUTHO_TOKEN, "empty");
        	Toast.makeText(this, "You are aloready Logged in with Auth_token:" + Auth_Token, Toast.LENGTH_SHORT).show();
        }
        
        authButton.setOnErrorListener(new OnErrorListener() {
        	   public void onError(FacebookException error) {Log.i("FB_ERROR", "Error " + error.getMessage());}
        });
        authButton.setReadPermissions(Arrays.asList("basic_info","email"));
        authButton.setSessionStatusCallback(new Session.StatusCallback() {
				public void call(Session session, SessionState state, Exception exception) {
        			if (session.isOpened()) {
        				Log.i("FB","Access Token"+ session.getAccessToken()); //**
        				final String accesstoken = session.getAccessToken();
        				//Log.i("FB","Access Token"+ session.); //**
        	              // make request to the /me API
                    	Request request = Request.newMeRequest(session, new Request.GraphUserCallback() {
                  		  // callback after Graph API response with user object
                  		  public void onCompleted(GraphUser user, Response response) {
                  		    if (user != null) {
                  		      sharedPrefEditor.putBoolean(AllConstants.PREF_KEY_FB_LOGIN, true);
                  		      sharedPrefEditor.putString(AllConstants.FB_USERNAME, user.getName());	//**
                  		      sharedPrefEditor.putString(AllConstants.FB_USERID, user.getId());	//**
                  		      String email = user.asMap().get("email").toString();
                  		      sharedPrefEditor.putString(AllConstants.FB_USER_EMAIL, email); //**
                  		     // Log.v("FB_LOGIN", user.getInnerJSONObject().toString());
                  		      sharedPrefEditor.commit();                 		   
                  		      Toast.makeText(LoginActivity.this, "Welcome " +  user.getName() + ". Your email: " + email, Toast.LENGTH_SHORT).show();
                  		      new authServerTask().execute(registration_url, user.getId(), user.getFirstName(),user.getLastName(), email, accesstoken);		
                  		    }
                  		  }
                  	});
                    	request.executeAsync();
        	          }
        	   }
          });
        	   
        //Act when twitter returns
        if (!isTwitterLoggedInAlready()) {
        	Uri uri = getIntent().getData();
        	if (uri != null && uri.toString().startsWith(AllConstants.TWITTER_CALLBACK_URL)) {
        		String verifier = uri.getQueryParameter(AllConstants.URL_TWITTER_OAUTH_VERIFIER);
        		new twitterAsyncTaskSecondRound().execute(verifier);
        	}
        }
        
        
	} //End of OnCreate
	
	//Call back for FB
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	  super.onActivityResult(requestCode, resultCode, data);
	  Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	public void loginToTwitter(View v){
		if (!isTwitterLoggedInAlready()) {
			if (!connection_status_detector.isConnectingToInternet()) {
				alert.showAlertDialog(LoginActivity.this, "Internet Connection Error","Please connect to working Internet connection", false);
				return;
			}
			new twitterAsyncTaskFirstRound().execute();
		} else {
			Toast.makeText(getApplicationContext(), "Already Logged into twitter", Toast.LENGTH_LONG).show();
		}
	}
	
	private boolean isTwitterLoggedInAlready() {
		//Consult shared preferences. Yet to be completed!
		return mSharedPreferences.getBoolean(AllConstants.PREF_KEY_TWITTER_LOGIN, false);
	}
	
	private class twitterAsyncTaskFirstRound extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... parameters) {
			ConfigurationBuilder builder = new ConfigurationBuilder();
			builder.setOAuthConsumerKey(AllConstants.TWITTER_CONSUMER_KEY);
			builder.setOAuthConsumerSecret(AllConstants.TWITTER_CONSUMER_SECRET);
			Configuration configuration = builder.build();
			TwitterFactory factory = new TwitterFactory(configuration);
			twitter = factory.getInstance();
			
			try{
				requestToken = twitter.getOAuthRequestToken(AllConstants.TWITTER_CALLBACK_URL);
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(requestToken.getAuthenticationURL())));
			} catch(TwitterException e){
				e.printStackTrace();
			}
			return null;
		}	
	} //End of twitterAsyncTask
	
	private class twitterAsyncTaskSecondRound extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... params) {
			String verifier = params[0];
			String username="";
			try {
				AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);
				// Save details in Preferences
				
				sharedPrefEditor.putString(AllConstants.PREF_KEY_OAUTH_TOKEN, accessToken.getToken());
				sharedPrefEditor.putString(AllConstants.PREF_KEY_OAUTH_SECRET,accessToken.getTokenSecret());
				// Store login status - true
				sharedPrefEditor.putBoolean(AllConstants.PREF_KEY_TWITTER_LOGIN, true);
				sharedPrefEditor.commit(); // save changes
				Log.e("Twitter OAuth Token", "> " + accessToken.getToken());
				long userID = accessToken.getUserId();
				User user = twitter.showUser(userID);
				username = user.getName();
				//accessToken.
				//
    		}catch(Exception e){
    			Log.e("Twitter Login Error", "> " + e.getMessage());
    		}
    		return username;
		}
		protected void onPostExecute(String name) {
		 Toast.makeText(LoginActivity.this, "Welcome " +  name + ". Your email: ", Toast.LENGTH_SHORT).show();
		}	
	} // End of twitterAsyncTaskSecondRound
	
	private class authServerTask extends AsyncTask<String, Void, String> {
		protected void onPreExecute() {
			super.onPreExecute();
			myProgressDialogManager.showProgresDialog(LoginActivity.this, "Registering...");
		}
		protected String doInBackground(String... parameters) {
			//String registration_url = getResources().getString(R.string.create_account);
			HTTPHelper myHTTPHelper = new HTTPHelper();
			String post_to_newuser_url = parameters[0];
			String uid = parameters[1];
			String firstname = parameters[2];
			String lastname = parameters[3];
			String email = parameters[4];
			String token = parameters[5];
			//Log.v("ASYNC_URL", post_to_newuser_url);
			String responseString = myHTTPHelper.postNewUser(post_to_newuser_url, uid, firstname, lastname, email, token);
	        return responseString;

		}
		protected void onPostExecute(String result) {
			myProgressDialogManager.dismissProgresDialog();
			//Toast.makeText(LoginActivity.this, result, Toast.LENGTH_SHORT).show();
			try {
				
				JSONObject userObject = new JSONObject(result);
				if(userObject.has("status") && userObject.getString("status").contentEquals("success")){
					
					if(userObject.has("auth_token") && !userObject.getString("auth_token").contentEquals("null")){
						sharedPrefEditor.putString(AllConstants.PREF_BARTER_LI_AUTHO_TOKEN, userObject.getString("auth_token"));
						sharedPrefEditor.commit();
						Log.v("AUTHO",  userObject.getString("auth_token"));
						Toast.makeText(getApplicationContext(), "Registration Success!::" + userObject.getString("auth_token"), Toast.LENGTH_LONG).show();
					}
					Intent latLongIntent = new Intent(LoginActivity.this, TakeLatLongActivity.class);
					startActivity(latLongIntent);
				} 
			} catch (JSONException e) {
				e.printStackTrace();
				Toast.makeText(LoginActivity.this, "Registration Failed!", Toast.LENGTH_SHORT).show();
			} 
			//Toast.makeText(LoginActivity.this, "Registration Success!", Toast.LENGTH_SHORT).show();
			Intent latLongIntent = new Intent(LoginActivity.this, TakeLatLongActivity.class);
			startActivity(latLongIntent);
		}
	} // End of askServerForSuggestionsTask
	
	/* public void LoginThroughFacebook(View v){
	if (!connection_status_detector.isConnectingToInternet()) {
		alert.showAlertDialog(LoginActivity.this, "Internet Connection Error","Please connect to working Internet connection", false);
		return;
	}

	// start Facebook Login
    Session.openActiveSession(this, true, new Session.StatusCallback() {
      // callback when session changes state
      public void call(Session session, SessionState state, Exception exception) {
        if (session.isOpened()) {
        	String accessToken = session.getAccessToken();
        
        	sharedPrefEditor.putString(AllConstants.FB_ACCESS_TOKEN, accessToken);
        	sharedPrefEditor.commit();

          // make request to the /me API
        	Request request = Request.newMeRequest(session, new Request.GraphUserCallback() {
      		  // callback after Graph API response with user object
      		  public void onCompleted(GraphUser user, Response response) {
      		    if (user != null) {
      		      sharedPrefEditor.putString(AllConstants.FB_USERNAME, user.getName());	
      		      sharedPrefEditor.putString(AllConstants.FB_USERID, user.getId());	
      		      //Few more details are yet to come!
      		      String email = user.getFirstName()+ user.asMap().get("email");
      		      sharedPrefEditor.putString(AllConstants.FB_USER_EMAIL, email);
      		      Log.v("FB_LOGIN", user.getInnerJSONObject().toString());
      		      sharedPrefEditor.commit();	
      		      welcome.setText("Hello " + user.getName() + "!. Email::" + email);
      		      //Save all details in preferences 
      		    }
      		  }
      	});
        	//Request.executeBatchAsync(request);
        	//request.setParameters(parameters);
        	request.executeAsync();
        }
      }
    });		
	} //End of LoginThroughFacebook */
	

}
