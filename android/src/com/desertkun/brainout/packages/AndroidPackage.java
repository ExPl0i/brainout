package com.desertkun.brainout.packages;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.desertkun.brainout.BrainOut;
import com.desertkun.brainout.utils.FileCopy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AndroidPackage extends ClientContentPackage
{
    public AndroidPackage(String name) throws ContentPackage.ValidationException
    {
        super(name);
    }

    public class AndroidFileHandle extends PackageFileHandle
    {
        private final String entryName;

        public AndroidFileHandle(String path)
        {
            super(path);

            this.entryName = path;
        }

        @Override
        public FileHandle handle()
        {
            try
            {
                File cacheDir = BrainOut.Env.getCacheDir();

                String ext = ".temp";

                int lastIndex = entryName.lastIndexOf('.');
                if (lastIndex > 0) {
                    ext = entryName.substring(lastIndex);
                }

                File f;
                if (cacheDir != null)
                {
                    f = File.createTempFile("zipFile" + crc32, ext, cacheDir);
                }
                else
                {
                    f = File.createTempFile("zipFile" + crc32, ext);
                }

                f.deleteOnExit();

                // The abstract PackageFileHandle has no read(); pull the entry's
                // bytes through the normal zip handle and copy them out so Android
                // can play the extracted file.
                try (InputStream in = AndroidPackage.super.getFile(entryName).read())
                {
                    FileCopy.copyFile(in, f);
                }

                return Gdx.files.absolute(f.getAbsolutePath());
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public PackageFileHandle getFile(String fileName)
    {
        if (fileName.endsWith(".mp3"))
        {
            // extract mp3's because Android can't load them from zip
            return new AndroidFileHandle(fileName);
        }

        return super.getFile(fileName);
    }
}
