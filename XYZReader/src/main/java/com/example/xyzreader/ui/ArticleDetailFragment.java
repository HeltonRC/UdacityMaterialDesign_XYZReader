package com.example.xyzreader.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private NestedScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private int mStatusBarFullOpacityBottom;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);
    private CoordinatorLayout mCoordinatorLayout;
    private AppBarLayout mAppBarLayout;
    private CollapsingToolbarLayout mCollapsingToolbarLayout;
    private Toolbar mDetailToolbar;
    private String mTitle;
    private FloatingActionButton mShareFab;
    private TextView mTitleView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mCoordinatorLayout = mRootView.findViewById(R.id.coordinator_layout);
        mScrollView = mRootView.findViewById(R.id.scrollview);

        mPhotoView = mRootView.findViewById(R.id.photo);
//        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);
        mShareFab = mRootView.findViewById(R.id.share_fab);

        mShareFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        if(getActivity().getIntent() != null) {
            ImageView photoView = (ImageView)  mRootView.findViewById(R.id.photo);
            photoView.setTransitionName("transitionname" + String.valueOf(mItemId));
        }

        mAppBarLayout = (AppBarLayout) mRootView.findViewById(R.id.photo_container_appbar);
        mCollapsingToolbarLayout = (CollapsingToolbarLayout) mRootView.findViewById(R.id.collapsing_toolbar_layout);
        mDetailToolbar = (Toolbar) mRootView.findViewById(R.id.detail_navigation_toolbar);

        mAppBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean isShow = false;
            int scrollRange = -1;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                float range = (float) -appBarLayout.getTotalScrollRange();
                mPhotoView.setImageAlpha((int) (255 * (1.0f - (float) verticalOffset / range)));

                if(scrollRange == -1) {
                    scrollRange = appBarLayout.getTotalScrollRange();
                }
                if(scrollRange + verticalOffset == 0) {
                    mCollapsingToolbarLayout.setTitle(mTitle);
                    mDetailToolbar.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.theme_primary));
                    mShareFab.hide();
                    mTitleView.setVisibility(View.INVISIBLE);
                    isShow = true;

                } else if (isShow) {
                    mCollapsingToolbarLayout.setTitle("");
                    mDetailToolbar.setBackgroundColor(Color.TRANSPARENT);
                    mShareFab.show();
                    mTitleView.setVisibility(View.VISIBLE);
                    isShow=false;
                }
            }
        });

        mDetailToolbar.setNavigationIcon(R.drawable.ic_arrow_back );
        mDetailToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        bindViews();
        return mRootView;
    }


    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        mTitleView = mRootView.findViewById(R.id.article_title);
        TextView bylineView = mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());
        TextView bodyView = mRootView.findViewById(R.id.article_body);
        bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));


        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            mTitle = mCursor.getString(ArticleLoader.Query.TITLE);
            //mCollapsingToolbarLayout.setTitle(mTitle);
            mTitleView.setText(mTitle);
            bylineView.setText(Html.fromHtml(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by <font color='#ffffff'>"
                            + mCursor.getString(ArticleLoader.Query.AUTHOR)
                            + "</font>"));

            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)));
           // bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY).replaceAll("(\r\n|\n)", "<br />")));

            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                Palette p = Palette.generate(bitmap, 12);
                                mMutedColor = p.getDarkMutedColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
                                mRootView.findViewById(R.id.meta_bar)
                                        .setBackgroundColor(mMutedColor);
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                scheduleStartPostponedTransition(mTitleView);
            }
        } else {
            mRootView.setVisibility(View.GONE);
            mTitleView.setText("N/A");
            bylineView.setText("N/A" );
            bodyView.setText("N/A");
        }
    }

    private void scheduleStartPostponedTransition(final View sharedElement) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sharedElement.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            sharedElement.getViewTreeObserver().removeOnPreDrawListener(this);
                            getActivity().startPostponedEnterTransition();
                            return true;
                        }
                    });
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        TransitionSet transitionSet = (TransitionSet) getSharedElementEnterTransition();
        if (transitionSet != null) {
            transitionSet.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionEnd(@NonNull Transition transition) {
                    // remove listener as otherwise there are side-effects
                    transition.removeListener(this);
                    // do something here
                    final int startScrollPos =
                            getResources().getDimensionPixelSize(
                                    R.dimen.init_scroll_up_distance);
                    Animator animator = ObjectAnimator.ofInt(
                            mScrollView,
                            "scrollY",
                            startScrollPos).setDuration(300);
                    animator.start();
                }

                @Override
                public void onTransitionStart(@NonNull Transition transition) {}
                @Override
                public void onTransitionCancel(@NonNull Transition transition) {}
                @Override
                public void onTransitionPause(@NonNull Transition transition) {}
                @Override
                public void onTransitionResume(@NonNull Transition transition) {}
            });
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

}
