package com.desertkun.brainout.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.desertkun.brainout.*;
import com.desertkun.brainout.client.settings.ClientSettings;

public class AndroidLauncher extends AndroidApplication
{
	@Override
	protected void onCreate (Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// If the app was opened via a brainout:// deep link, hand the encoded
		// location to the client before the engine starts. IntroMenu picks up
		// ConnectToLocation and connects directly (HashedUrl handles the
		// brainout:// prefix and the base64 host;tcp;udp;http payload).
		handleConnectIntent(getIntent());

		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();

        ClientSettings clientSettings = new AndroidSettings();
        clientSettings.init();

		initialize(BrainOutAndroid.initAndroidInstance(new AndroidEnvironment(getContext()),
                clientSettings), config);
	}

	private void handleConnectIntent(Intent intent)
	{
		if (intent == null)
			return;

		Uri data = intent.getData();
		if (data != null && "brainout".equals(data.getScheme()))
		{
			BrainOutClient.ConnectToLocation = data.toString();
		}
	}
}
