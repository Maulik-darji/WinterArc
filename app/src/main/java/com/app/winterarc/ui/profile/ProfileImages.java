package com.app.winterarc.ui.profile;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns a picked photo into the shared profile format (same as the website): a 400x400 avatar or a
 * 1500x500 banner, centre-cropped, upright, JPEG. Large photos are decoded at reduced size.
 */
public final class ProfileImages {
    public static final String AVATAR = "avatar";
    public static final String BANNER = "banner";

    private ProfileImages() {}

    @Nullable
    @WorkerThread
    public static byte[] prepare(ContentResolver resolver, Uri uri, String kind) {
        int targetW = AVATAR.equals(kind) ? 400 : 1500;
        int targetH = AVATAR.equals(kind) ? 400 : 500;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH);
            Bitmap decoded;
            try (InputStream in = resolver.openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) return null;
            Bitmap upright = rotate(decoded, orientation(resolver, uri));
            Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            float scale = Math.max((float) targetW / upright.getWidth(), (float) targetH / upright.getHeight());
            float w = upright.getWidth() * scale;
            float h = upright.getHeight() * scale;
            new Canvas(out).drawBitmap(upright, null,
                    new RectF((targetW - w) / 2f, (targetH - h) / 2f, (targetW + w) / 2f, (targetH + h) / 2f),
                    new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            if (upright != decoded) upright.recycle();
            decoded.recycle();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, 86, bytes);
            out.recycle();
            return bytes.toByteArray();
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    static int sampleSize(int w, int h, int targetW, int targetH) {
        int sample = 1;
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2;
        return sample;
    }

    private static int orientation(ContentResolver resolver, Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotate(Bitmap source, int orientation) {
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
            case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
            case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            default: return source;
        }
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, true);
    }
}
