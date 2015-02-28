package com.pr0gramm.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.ViewPropertyAnimator;

import com.pr0gramm.app.feed.FeedProxy;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;


/**
 * This is the main class of our pr0gramm app.
 */
@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity {

    @InjectView(R.id.drawer_layout)
    private DrawerLayout drawerLayout;

    @InjectView(R.id.toolbar)
    private Toolbar toolbar;

    private ActionBarDrawerToggle drawerToggle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // use toolbar as action bar
        setSupportActionBar(toolbar);
        getSupportActionBar().setShowHideAnimationEnabled(true);

        // prepare drawer layout
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
        drawerLayout.setDrawerListener(drawerToggle);

        // load feed-fragment into view
        if (savedInstanceState == null) {
            gotoFeedFragment();
            createDrawerFragment();
        }
    }

    private void createDrawerFragment() {
        DrawerFragment fragment = new DrawerFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.left_drawer, fragment)
                .commit();
    }

    private void gotoFeedFragment() {
        FeedFragment fragment = new FeedFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .commit();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START | Gravity.LEFT)) {
            drawerLayout.closeDrawers();
            return;
        }

        super.onBackPressed();
    }

    public void onPostClicked(FeedProxy feed, int idx) {
        Fragment fragment = PostPagerFragment.newInstance(feed, idx);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
    }

    public class ScrollHideToolbarListener {
        private int toolbarMarginOffset = 0;
        private ViewPropertyAnimator animation;

        private ScrollHideToolbarListener() {
            // only this activity can instantiate this listener
        }

        private void applyToolbarPosition(boolean animated) {
            // ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
            // params.topMargin = -1 * toolbarMarginOffset;
            // toolbar.setLayoutParams(params);

            int y = -toolbarMarginOffset;
            if (animated) {
                animation = toolbar.animate().translationY(y).setDuration(250);
                animation.start();
            } else {
                if (animation != null) {
                    animation.cancel();
                    animation = null;
                }

                toolbar.setTranslationY(y);
            }
        }

        public void onScrolled(int dy) {
            int abHeight = AndroidUtility.getActionBarSize(MainActivity.this);

            toolbarMarginOffset += dy;
            if (toolbarMarginOffset > abHeight)
                toolbarMarginOffset = abHeight;

            if (toolbarMarginOffset < 0)
                toolbarMarginOffset = 0;

            applyToolbarPosition(false);
        }

        public void reset() {
            if (toolbarMarginOffset != 0) {
                toolbarMarginOffset = 0;
                applyToolbarPosition(true);
            }
        }
    }

    public final ScrollHideToolbarListener onScrollHideToolbarListener = new ScrollHideToolbarListener();
}
