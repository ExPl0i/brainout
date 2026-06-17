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

        AndroidEnvironment environment = new AndroidEnvironment(getContext());

        ClientSettings clientSettings = new AndroidSettings(environment, getContext());
        clientSettings.init();

        BrainOutAndroid app = BrainOutAndroid.initAndroidInstance(environment, clientSettings);

        // This Android port only ever talks to self-hosted servers (direct
        // connect / deep link), never the Anthill online backend — whose HTTP
        // client is incompatible with Android's stripped org.apache.http anyway.
        // So it always runs offline (short-circuits CSOnlineInit) and always
        // accepts the bundled unsigned data packages (no --unsafe CLI on Android,
        // and data signing is moot without the online backend). This applies to
        // release builds too, which are not debuggable.
        app.offline = true;
        app.unsafe = true;

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
