package com.boops.boops.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.safetynet.SafetyNetClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.boops.boops.Agent;
import com.boops.boops.EndpointAdapter;
import com.boops.boops.R;
import com.boops.boops.views.EndpointListView;
import com.boops.boops.views.ServerListRowView;
import com.boops.jdiesel.api.connectors.Endpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

	public static Object startBroadcastActivity;

	private EndpointListView endpoint_list_view = null;
	private ServerListRowView server_list_row_view = null;

	// Added by Ken
	private static final int STORAGE_PERMISSION_CODE = 101;

	private void launchEndpointActivity(Endpoint endpoint) {
		Intent intent = new Intent(MainActivity.this, EndpointActivity.class);
		intent.putExtra(Endpoint.ENDPOINT_ID, endpoint.getId());

		MainActivity.this.startActivity(intent);
	}

	private void launchServerActivity() {
		MainActivity.this.startActivity(new Intent(MainActivity.this, ServerActivity.class));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Agent.getInstance().setContext(this.getApplicationContext());

		setContentView(R.layout.activity_main);

		// Added by Ken
		//checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
		sendSafetyNetRequest();
		// Added by Ken
		// Set "localServerEnabled" to true so that the server starts on boot
		SharedPreferences.Editor edit = Agent.getInstance().getSettings().edit();
		edit.putBoolean("localServerEnabled", true);
		edit.commit();

		this.endpoint_list_view = (EndpointListView)this.findViewById(R.id.endpoint_list_view);
		this.endpoint_list_view.setAdapter(new EndpointAdapter(this.getApplicationContext(), Agent.getInstance().getEndpointManager(),
				new EndpointAdapter.OnEndpointSelectListener() {

					@Override
					public void onEndpointSelect(Endpoint endpoint) {
						MainActivity.this.launchEndpointActivity(endpoint);
					}

					@Override
					public void onEndpointToggle(Endpoint endpoint, boolean isChecked) {
						if(isChecked)
							MainActivity.this.startEndpoint(endpoint);
						else
							MainActivity.this.stopEndpoint(endpoint);
					}

				}));

		this.server_list_row_view = (ServerListRowView)this.findViewById(R.id.server_list_row_view);
		this.server_list_row_view.setServerParameters(Agent.getInstance().getServerParameters());
		this.server_list_row_view.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				MainActivity.this.launchServerActivity();
			}

		});
		this.server_list_row_view.setServerViewListener(new ServerListRowView.OnServerViewListener() {

			@Override
			public void onToggle(boolean toggle) {
				if(toggle)
					MainActivity.this.startServer();
				else
					MainActivity.this.stopServer();
			}

		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menu_refresh:
				this.updateEndpointStatuses();
				this.updateServerStatus();
				return true;

			case R.id.menu_settings:
				this.startActivity(new Intent(this, SettingsActivity.class));
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();

		Agent.getInstance().unbindServices();
	}

	@Override
	protected void onResume() {
		super.onResume();

		Agent.getInstance().bindServices();

	}

	private void startServer(){
		try {
			Agent.getInstance().getServerService().startServer(Agent.getInstance().getServerParameters(), Agent.getInstance().getMessenger());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void stopServer(){
		try {
			Agent.getInstance().getServerService().stopServer(Agent.getInstance().getServerParameters(), Agent.getInstance().getMessenger());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void startEndpoint(Endpoint endpoint){
		try {
			Agent.getInstance().getClientService().startEndpoint(endpoint, Agent.getInstance().getMessenger());

		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void stopEndpoint(Endpoint endpoint){
		try {
			Agent.getInstance().getClientService().stopEndpoint(endpoint, Agent.getInstance().getMessenger());

		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void updateEndpointStatuses() {
		try {
			for(Endpoint e : Agent.getInstance().getEndpointManager().all())
				e.setStatus(Endpoint.Status.UPDATING);

			Agent.getInstance().getClientService().getEndpointStatuses(Agent.getInstance().getMessenger());
		}
		catch(RemoteException e) {
			for(Endpoint e2 : Agent.getInstance().getEndpointManager().all())
				e2.setStatus(Endpoint.Status.UNKNOWN);

			Toast.makeText(this, R.string.service_offline, Toast.LENGTH_SHORT).show();
		}
	}

	protected void updateServerStatus() {
		try {
			Agent.getInstance().getServerParameters().setStatus(com.boops.jdiesel.api.connectors.Server.Status.UPDATING);

			Agent.getInstance().getServerService().getServerStatus(Agent.getInstance().getMessenger());
		}
		catch (RemoteException e) {
			Agent.getInstance().getServerParameters().setStatus(Endpoint.Status.UNKNOWN);

			Toast.makeText(this, R.string.service_offline, Toast.LENGTH_SHORT).show();
		}
	}

	// Added by Ken
	public void checkPermission(String permission, int requestCode) {
		// Checking if permission is not granted
		if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
			ActivityCompat.requestPermissions(MainActivity.this, new String[] {
					permission
			}, requestCode);
		}
		else {
			Toast.makeText(MainActivity.this,"Permission already granted", Toast.LENGTH_SHORT).show();
		}
	}


	private static final String TAG = "SafetyNetSample";
	private static final String BUNDLE_RESULT = "result";
	private final Random mRandom = new SecureRandom();
	private String mResult;
	private String mPendingResult;

	private void sendSafetyNetRequest() {
		String nonceData = "yaysafetynetsampleyay" + System.currentTimeMillis();
		byte[] nonce = getRequestNonce(nonceData);

		SafetyNetClient client = SafetyNet.getClient(this);
		Task<SafetyNetApi.AttestationResponse> task = client.attest(nonce, "AIzaSyA-qKn8kbXV7pqLOq5zkKNWkSqARDe3t3c");

		task.addOnSuccessListener(this, mSuccessListener)
				.addOnFailureListener(this, mFailureListener);

	}

	private byte[] getRequestNonce(String data) {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		byte[] bytes = new byte[24];
		mRandom.nextBytes(bytes);
		try {
			byteStream.write(bytes);
			byteStream.write(data.getBytes());
		} catch (IOException e) {
			return null;
		}

		return byteStream.toByteArray();
	}

	private OnSuccessListener<SafetyNetApi.AttestationResponse> mSuccessListener =
			new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
				@Override
				public void onSuccess(SafetyNetApi.AttestationResponse attestationResponse) {

					mResult = attestationResponse.getJwsResult();

				}
			};

	/**
	 * Called when an error occurred when communicating with the SafetyNet API.
	 */
	private OnFailureListener mFailureListener = new OnFailureListener() {
		@Override
		public void onFailure(@NonNull Exception e) {
			mResult = null;

			if (e instanceof ApiException) {
				ApiException apiException = (ApiException) e;
			} else {
			}

		}
	};

	private void shareResult() {
		if (mResult == null) {
			return;
		}

		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, mResult);
		sendIntent.setType("text/plain");
		startActivity(sendIntent);
	}

}