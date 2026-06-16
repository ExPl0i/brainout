package com.desertkun.brainout.android;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
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

        AndroidEnvironment environment = new AndroidEnvironment(getContext());

        ClientSettings clientSettings = new AndroidSettings(environment, getContext());
        clientSettings.init();

        BrainOutAndroid app = BrainOutAndroid.initAndroidInstance(environment, clientSettings);

        // This port targets self-hosted servers (direct connect / deep link), not
        // the Anthill online backend — whose HTTP client is incompatible with
        // Android's stripped org.apache.http anyway. Offline mode short-circuits
        // CSOnlineInit so startup reaches the menu without that backend.
        app.offline = true;

        // Android has no --unsafe CLI flag. Accept unsigned data packages on
        // debuggable (debug) builds so testing against a self-hosted server
        // works without bundling a signing key; release builds keep signature
        // verification (see docs/AndroidPort.md, Phase 5).
        app.unsafe = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

		initialize(app, config);
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
