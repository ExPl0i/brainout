package com.desertkun.brainout;

import android.content.Context;
import android.provider.Settings;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.desertkun.brainout.controllers.GameController;
import com.desertkun.brainout.online.KryoNetworkClient;
import com.desertkun.brainout.online.NetworkClient;
import com.desertkun.brainout.online.NetworkConnectionListener;
import com.desertkun.brainout.packages.ZipContentPackage;
import com.desertkun.brainout.utils.FileCopy;
import com.esotericsoftware.kryo.Kryo;

import java.io.File;
import java.io.IOException;

public class AndroidEnvironment extends ClientEnvironment
{
    private final Context context;
    private final AndroidGameController androidGameController;

    public AndroidEnvironment(Context context)
    {
        this.context = context;
        this.androidGameController = new AndroidGameController();
    }

    @Override
    public String getUniqueId()
    {
        // Stable per-install device identifier; replaces the old reflection into
        // the hidden android.os.SystemProperties API (which no longer works).
        String id = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        return id != null ? id : "";
    }

    @Override
    public GameUser newUser()
    {
        return new GameUser();
    }

    @Override
    public String getStoreComponent()
    {
        return null;
    }

    @Override
    public String getExternalPath(String from)
    {
        // App-private internal storage: writable without any runtime permission
        // and not broken by scoped storage on Android 10+. Must share the base
        // that Gdx.files.local uses on Android (the app files dir) so that
        // package unpacking and PackageManager.searchPackages() — which scans
        // Gdx.files.local("packages") — resolve to the same location.
        return new File(context.getFilesDir(), from).getAbsolutePath();
    }

    @Override
    public File getFile(String path)
    {
        return new File(path);
    }

    @Override
    public File getCacheDir()
    {
        return context.getCacheDir();
    }

    @Override
    public void init()
    {
        super.init();

        File mainMenu = ZipContentPackage.packageFile(ClientConstants.Client.MAINMENU_PACKAGE);
        FileHandle internal = Gdx.files.internal(ZipContentPackage.packageFilename(ClientConstants.Client.MAINMENU_PACKAGE));

        if (!mainMenu.exists())
        {
            mainMenu.getParentFile().mkdirs();

            if (internal.exists())
            {
                try
                {
                    FileCopy.copyFile(internal.read(), mainMenu);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                // it's bad
                throw new RuntimeException("Mainmenu package is unable to copy.");
            }
        }
    }

    @Override
    public GameController getGameController()
    {
        return androidGameController;
    }

    @Override
    public NetworkClient newNetworkClient(Kryo kryo, NetworkConnectionListener listener)
    {
        return new KryoNetworkClient(kryo, listener);
    }
}
