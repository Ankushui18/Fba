package io.github.ankushui18.fbaoffline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

final class ImageFiles {
    private ImageFiles() {}

    static void write(File destination, Bitmap image) throws IOException {
        Files.createDirectories(destination.getParentFile().toPath());
        AtomicFile file = new AtomicFile(destination);
        FileOutputStream out = file.startWrite();
        boolean success = false;
        try {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("Couldn't save the image.");
            success = true;
        } finally {
            if (success) file.finishWrite(out);
            else file.failWrite(out);
        }
    }

    static void importArt(InputStream in, File destination) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        for (int n; (n = in.read(buffer)) != -1;) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Artwork import cancelled.");
            if (data.size() + n > 8 * 1024 * 1024) throw new IOException("Choose a PNG or JPEG smaller than 8 MiB.");
            data.write(buffer, 0, n);
        }
        byte[] source = data.toByteArray();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 8192 || options.outHeight > 8192
                || !("image/png".equals(options.outMimeType) || "image/jpeg".equals(options.outMimeType))) {
            throw new IOException("Choose a valid PNG or JPEG image.");
        }
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 800) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (bitmap == null) throw new IOException("Couldn't decode this image.");
        try { write(destination, bitmap); } finally { bitmap.recycle(); }
    }
}
