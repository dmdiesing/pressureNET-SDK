package ca.cumulonimbus.pressurenetsdk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Message;
import android.os.Messenger;

/**
 * Make pressureNET Live API calls
 * and manage the results locally
 * 
 * @author jacob
 *
 */
public class CbApi {
	
	Context context;
	String apiServerURL = "https://pressurenet.cumulonimbus.ca/live/?"; 
	private CbDb db;
	private CbApiCall apiCall;
	private ArrayList<CbObservation> callResults = new ArrayList<CbObservation>();
	
	private Messenger replyResult = null;
	
	private CbService caller;
	
	
	
	/**
	 * Make an API call and store the results
	 * @return
	 */
	public boolean makeAPICall(CbApiCall call, CbService caller, Messenger ms) {
		
		this.replyResult = ms;
		this.caller = caller;
		apiCall = call;
		APIDataDownload api = new APIDataDownload();
		api.setReplyToApp(ms);
		api.execute("");
		
		return true;
	}
	
	
	/**
	 * When an API call finishes we'll have an ArrayList of results.
	 * Save them into the database
	 * @param results
	 * @return
	 */
	private boolean saveAPIResults(ArrayList<CbObservation> results) {
		db.open();
		System.out.println("saving api results...");
		
		db.addObservationArrayList(results);
		
		db.close();
		return false;
	}
	
	public CbApi (Context ctx) {
		context = ctx;
		db = new CbDb(context);
	}
	
	private class APIDataDownload extends AsyncTask<String, String, String> {

		Messenger replyToApp = null;
		
		
		
		public Messenger getReplyToApp() {
			return replyToApp;
		}

		public void setReplyToApp(Messenger replyToApp) {
			this.replyToApp = replyToApp;
		}

		@Override
		protected String doInBackground(String... arg0) {
			String responseText = "";
			try {
				DefaultHttpClient client = new DefaultHttpClient();
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("min_lat", apiCall.getMinLat() + ""
						+ ""));
				nvps.add(new BasicNameValuePair("max_lat", apiCall.getMaxLat() + ""
						+ ""));
				nvps.add(new BasicNameValuePair("min_lon", apiCall.getMinLon() + ""
						+ ""));
				nvps.add(new BasicNameValuePair("max_lon", apiCall.getMaxLon() + ""
						+ ""));
				nvps.add(new BasicNameValuePair("start_time", apiCall.getStartTime() + ""));
				nvps.add(new BasicNameValuePair("end_time", apiCall.getEndTime() + ""));
				nvps.add(new BasicNameValuePair("api_key", apiCall.getApiKey()));
				nvps.add(new BasicNameValuePair("format", apiCall.getFormat()));
				nvps.add(new BasicNameValuePair("limit", apiCall.getLimit() + "")); 
				nvps.add(new BasicNameValuePair("global", apiCall.isGlobal() + ""));
				nvps.add(new BasicNameValuePair("since_last_call", apiCall.isSinceLastCall() + ""));
				
				String paramString = URLEncodedUtils.format(nvps, "utf-8");

				apiServerURL = apiServerURL + paramString;
				System.out.println("cbservice api sending " + apiServerURL);
				HttpGet get = new HttpGet(apiServerURL);
				// Execute the GET call and obtain the response
				HttpResponse getResponse = client.execute(get);
				HttpEntity responseEntity = getResponse.getEntity();
				System.out.println("response " + responseEntity.getContentLength());

				BufferedReader r = new BufferedReader(new InputStreamReader(
						responseEntity.getContent()));

				StringBuilder total = new StringBuilder();
				String line;
				if (r != null) {
					while ((line = r.readLine()) != null) {
						total.append(line);
					}
					responseText = total.toString();
				}
			} catch (Exception e) {
				System.out.println("api error");
				e.printStackTrace();
			}
			return responseText;
		}

		protected void onPostExecute(String result) {
			callResults = processJSONResult(result);
			saveAPIResults(callResults);
			System.out.println("saved " + callResults.size() + " api call results");
			caller.notifyAPIResult(replyToApp, callResults.size());
		}
	}
	
	/**
	 * Take a JSON string and return the data in a useful structure
	 * 
	 * @param resultJSON
	 */
	private ArrayList<CbObservation> processJSONResult(String resultJSON) {
		ArrayList<CbObservation> obsFromJSON = new ArrayList<CbObservation>();
		System.out.println("processing json result");
		try {
			JSONArray jsonArray = new JSONArray(resultJSON);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				CbObservation singleObs = new CbObservation();
				try {
					Location location = new Location("network");
					location.setLatitude(jsonObject.getDouble("latitude"));
					location.setLongitude(jsonObject.getDouble("longitude"));
					location.setAccuracy((float) jsonObject
							.getDouble("location_accuracy"));
					singleObs.setLocation(location);
					singleObs.setTime(jsonObject.getLong("daterecorded"));
					singleObs.setTimeZoneOffset(jsonObject
							.getLong("tzoffset"));
					singleObs.setSharing(jsonObject.getString("sharing"));
					singleObs.setUser_id(jsonObject.getString("user_id"));
					singleObs.setObservationValue(jsonObject
							.getDouble("reading"));
					obsFromJSON.add(singleObs);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// TODO: Add dates and trends prior to graphing.
			//ArrayList<CbObservation> detailedList = CbObservation.addDatesAndTrends(apiCbObservationResults);
			//recents = CbObservation.addDatesAndTrends(apiCbObservationResults);
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return obsFromJSON;
	}
	
}
