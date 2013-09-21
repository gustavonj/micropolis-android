package micropolis.android;

import micropolisj.engine.*;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.AttributeSet;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.*;
import android.widget.OverScroller;

public class MicropolisView extends View
{
	Micropolis city;
	Rect scrollBounds = new Rect();
	Matrix renderMatrix = new Matrix();

	int windowWidth;
	int windowHeight;

	TileHelper tiles;
	int tileSize = 32;
	float originX = 0.0f;
	float originY = 0.0f;

	float scaleFocusX = 0.0f;
	float scaleFocusY = 0.0f;
	float scaleFactor = 1.0f;

	MicropolisTool currentTool = null;

	public MicropolisView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		tiles = new TileHelper(context, tileSize);
	}

	public void setCity(Micropolis newCity)
	{
		this.city = newCity;

		scrollBounds.left = 0;
		scrollBounds.top = 0;
		scrollBounds.right = tileSize*city.getWidth();
		scrollBounds.bottom = tileSize*city.getHeight();

		originX = (scrollBounds.left + scrollBounds.right) / 2.0f;
		originY = (scrollBounds.top + scrollBounds.bottom) / 2.0f;

		city.addMapListener(new MapListener() {
			public void mapOverlayDataChanged(MapState overlayDataType) {}
			public void spriteMoved(Sprite sprite) {}
			public void tileChanged(int xpos, int ypos) {
				onTileChanged(xpos, ypos);
			}
			public void wholeMapChanged() {
				invalidate();
			}
			});
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		windowWidth = w;
		windowHeight = h;

		updateRenderMatrix();
	}

	public void setTileSize(int newSize)
	{
		double f = ((double)newSize) / ((double)tileSize);
		scaleFactor /= f;
		originX *= f;
		originY *= f;

		this.tileSize = newSize;
		tiles.changeTileSize(tileSize);
		updateRenderMatrix();	
		invalidate();
	}

	private void updateRenderMatrix()
	{
		renderMatrix.reset();
		renderMatrix.preTranslate(windowWidth/2, windowHeight/2);
		if (scaleFactor != 1.0f) {
			renderMatrix.preScale(scaleFactor, scaleFactor, scaleFocusX, scaleFocusY);
		}
		renderMatrix.preTranslate(-originX, -originY);
	}

	Rect getTileBounds(int xpos, int ypos)
	{
		float [] pts = {
			xpos * tileSize,
			ypos * tileSize,
			(xpos+1) * tileSize,
			(ypos+1) * tileSize
			};
		renderMatrix.mapPoints(pts);
		return new Rect(
			(int)Math.floor(pts[0]),
			(int)Math.floor(pts[1]),
			(int)Math.ceil(pts[2]),
			(int)Math.ceil(pts[3])
			);
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		Paint p = new Paint();

		canvas.save();
		canvas.concat(renderMatrix);

		Rect bounds = canvas.getClipBounds();
		int minY = bounds.top / tileSize;
		int maxY = bounds.bottom / tileSize + 1;
		int minX = bounds.left / tileSize;
		int maxX = bounds.right / tileSize + 1;

		minY = Math.max(minY, 0);
		maxY = Math.min(maxY, city.getHeight());
		minX = Math.max(minX, 0);
		maxX = Math.min(maxX, city.getWidth());

		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x < maxX; x++) {
				int t = city.getTile(x, y) & TileConstants.LOMASK;
				tiles.drawTo(canvas, t, x, y);
			}
		}

		canvas.restore();
//		if (activeScroller != null) {
//			String s = "Scroller: " + activeScroller.toString();
//			canvas.drawText(s,
//				0.0f, 25.0f,
//				p);
//		}
	}

	class MyGestureListener extends GestureDetector.SimpleOnGestureListener
			implements ScaleGestureDetector.OnScaleGestureListener
	{
		@Override
		public boolean onDown(MotionEvent ev)
		{
			stopMomentum();
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
		{
			originX += distanceX / scaleFactor;
			originY += distanceY / scaleFactor;

			if (originX < scrollBounds.left) {
				originX = scrollBounds.left;
			}
			if (originX > scrollBounds.right) {
				originX = scrollBounds.right;
			}

			if (originY < scrollBounds.top) {
				originY = scrollBounds.top;
			}
			if (originY > scrollBounds.bottom) {
				originY = scrollBounds.bottom;
			}

			updateRenderMatrix();
			invalidate();
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			startMomentum(velocityX / scaleFactor, velocityY / scaleFactor);
			return true;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent evt)
		{
			processTool(evt.getX(), evt.getY());
			return true;
		}

		// implements OnScaleGestureListener
		public boolean onScale(ScaleGestureDetector d)
		{
			scaleFocusX = d.getFocusX() - windowWidth/2;
			scaleFocusY = d.getFocusY() - windowHeight/2;
			scaleFactor *= d.getScaleFactor();
			scaleFactor = Math.min(Math.max(scaleFactor, 0.5f), 2.0f);
			updateRenderMatrix();
			invalidate();
			return true;
		}

		// implements OnScaleGestureListener
		public boolean onScaleBegin(ScaleGestureDetector d)
		{
			return true;
		}

		// implements OnScaleGestureListener
		public void onScaleEnd(ScaleGestureDetector d)
		{
			if (tileSize == 32 && scaleFactor < 0.51) {
				setTileSize(8);
			}
			else if (tileSize == 8 && scaleFactor > 1.99) {
				setTileSize(32);
			}
		}
	}
	MyGestureListener mgl = new MyGestureListener();
	GestureDetector gestDetector = new GestureDetector(getContext(), mgl);
	ScaleGestureDetector scaleDetector = new ScaleGestureDetector(getContext(), mgl);

	@Override
	public boolean onTouchEvent(MotionEvent evt)
	{
		boolean x1 = gestDetector.onTouchEvent(evt);
		boolean x2 = scaleDetector.onTouchEvent(evt);
		return x1 || x2;
	}

	Runnable activeScroller = null;
	Handler myHandler = new Handler();

	class MyScrollStep implements Runnable
	{
		OverScroller s = new OverScroller(getContext());

		MyScrollStep(float velX, float velY)
		{
			s.fling((int)originX, (int)originY, (int)-velX, (int)-velY,
				scrollBounds.left, scrollBounds.right,
				scrollBounds.top, scrollBounds.bottom,
				tileSize*4, tileSize*4);
		}

		public void run()
		{
			if (activeScroller == this) {

				boolean activ = s.computeScrollOffset();

				originX = s.getCurrX();
				originY = s.getCurrY();
				updateRenderMatrix();
				invalidate();

				if (!activ) {
					activeScroller = null;
				}
				else {
					myHandler.postDelayed(this, 100);
				}
			}
		}

		@Override
		public String toString()
		{
			return "X: "+s.getCurrX()+" ("+s.getFinalX()+")";
		}
	}

	private void startMomentum(float velX, float velY)
	{
		this.activeScroller = new MyScrollStep(velX, velY);
		myHandler.postDelayed(activeScroller, 100);
	}

	private void stopMomentum()
	{
		this.activeScroller = null;
	}

	private void processTool(float x, float y)
	{
		try {

		CityLocation loc = getLocation(x, y);
		if (currentTool != null) {
			currentTool.apply(city, loc.x, loc.y);
		}

		}
		catch (Throwable e)
		{
			AlertDialog alert = new AlertDialog.Builder(getContext()).create();
			alert.setTitle("Error");
			alert.setMessage(e.toString());
			alert.show();
		}
	}

	private CityLocation getLocation(float x, float y)
	{
		Matrix aMatrix = new Matrix();
		if (!renderMatrix.invert(aMatrix)) {
			return new CityLocation(0,0);
		}

		float [] pts = new float[] { x, y };
		aMatrix.mapPoints(pts);

		return new CityLocation(
			(int)pts[0] / tileSize,
			(int)pts[1] / tileSize
			);
	}

	void setTool(MicropolisTool tool)
	{
		this.currentTool = tool;
	}

	private void onTileChanged(int xpos, int ypos)
	{
		Rect r = getTileBounds(xpos, ypos);
		invalidate(r);
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		Bundle b = new Bundle();
		b.putParcelable("superState", super.onSaveInstanceState());
		b.putFloat("scaleFactor", scaleFactor);
		b.putFloat("originX", originX);
		b.putFloat("originY", originY);
		return b;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		if (state instanceof Bundle) {
			Bundle b = (Bundle) state;
			super.onRestoreInstanceState(b.getParcelable("superState"));
			scaleFactor = b.getFloat("scaleFactor");
			originX = b.getFloat("originX");
			originY = b.getFloat("originY");

			if (scaleFactor < 0.5f) {
				scaleFactor = 0.5f;
			}
			updateRenderMatrix();
		}
		else {
			super.onRestoreInstanceState(state);
		}
	}

}
