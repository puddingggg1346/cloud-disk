package xyan.yun;

import android.graphics.*;
import android.graphics.drawable.Drawable;

public class NotchOutlineDrawable extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final float d;
  private final float radiusDp = 8, strokeDp = 2;
  private float notchStartDp = 14, notchEndDp = 46;
  private boolean showNotch = false;

  public NotchOutlineDrawable() {
    d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(strokeDp * d);
    paint.setColor(0xFFFFFFFF);
  }

  public void setShowNotch(boolean s) {
    if (showNotch != s) { showNotch = s; invalidateSelf(); }
  }

  @Override public void draw(Canvas c) {
    Rect b = getBounds();
    float w = b.width(), h = b.height();
    float r = radiusDp * d, half = strokeDp * d / 2;
    float top = half, left = half, right = w - half, bottom = h - half;
    float ns = notchStartDp * d, ne = notchEndDp * d;
    float topLeftStart = showNotch ? ne : (left + r);
    float topLeftEnd   = showNotch ? ns : (left + r);

    path.reset();
    path.moveTo(topLeftStart, top);
    path.lineTo(right - r, top);
    path.arcTo(new RectF(right - 2*r, top, right, top + 2*r), -90, 90);
    path.lineTo(right, bottom - r);
    path.arcTo(new RectF(right - 2*r, bottom - 2*r, right, bottom), 0, 90);
    path.lineTo(left + r, bottom);
    path.arcTo(new RectF(left, bottom - 2*r, left + 2*r, bottom), 90, 90);
    path.lineTo(left, top + r);
    path.arcTo(new RectF(left, top, left + 2*r, top + 2*r), 180, 90);
    path.lineTo(topLeftEnd, top);
    c.drawPath(path, paint);
  }

  @Override public void setAlpha(int a){ paint.setAlpha(a); }
  @Override public void setColorFilter(ColorFilter cf){ paint.setColorFilter(cf); }
  @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }
}
