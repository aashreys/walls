/*
 * Copyright {2017} {Aashrey Kamal Sharma}
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.aashreys.walls.application.activities;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.DimenRes;
import android.text.SpannableString;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ImageView;

import com.aashreys.maestro.ViewModel;
import com.aashreys.walls.R;
import com.aashreys.walls.utils.SchedulerProvider;
import com.aashreys.walls.application.helpers.ImageDownloader;
import com.aashreys.walls.application.helpers.ImageInfoBuilder;
import com.aashreys.walls.application.views.InfoView;
import com.aashreys.walls.domain.device.DeviceInfo;
import com.aashreys.walls.domain.device.ResourceProvider;
import com.aashreys.walls.domain.display.images.Image;
import com.aashreys.walls.domain.display.images.ImageService;
import com.aashreys.walls.domain.display.images.metadata.User;
import com.aashreys.walls.domain.share.ShareDelegate;
import com.aashreys.walls.domain.share.ShareDelegateFactory;
import com.aashreys.walls.persistence.favoriteimage.FavoriteImageRepository;
import com.aashreys.walls.utils.LogWrapper;
import com.bumptech.glide.Priority;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.CompletableObserver;
import io.reactivex.SingleObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/**
 * Created by aashreys on 15/04/17.
 */

public class ImageActivityModel implements ViewModel {

    private static final String TAG = ImageActivityModel.class.getSimpleName();

    private final ImageInfoBuilder imageInfoBuilder;

    private final ShareDelegateFactory shareDelegateFactory;

    private final FavoriteImageRepository favoriteImageRepository;

    private final ImageService imageService;

    private final DeviceInfo deviceInfo;

    private final ImageDownloader imageDownloader;

    private final ResourceProvider resourceProvider;

    private ImageView imageView;

    private EventListener eventListener;

    private Image image;

    private ShareDelegate shareLinkDelegate, copyLinkDelegate,
            setAsShareDelegate;

    private boolean isImageLoaded, isImageInfoLoaded, isFavorite;

    private Disposable imageInfoDisposable, imageDrawableDisposable;

    private final CompositeDisposable shareCompositeDisposable;

    private final SchedulerProvider schedulerProvider;

    @Inject
    ImageActivityModel(
            ImageInfoBuilder imageInfoBuilder,
            ShareDelegateFactory shareDelegateFactory,
            FavoriteImageRepository favoriteImageRepository,
            ImageService imageService,
            DeviceInfo deviceInfo,
            ImageDownloader imageDownloader,
            ResourceProvider resourceProvider,
            SchedulerProvider schedulerProvider
    ) {
        this.imageInfoBuilder = imageInfoBuilder;
        this.shareDelegateFactory = shareDelegateFactory;
        this.favoriteImageRepository = favoriteImageRepository;
        this.imageService = imageService;
        this.deviceInfo = deviceInfo;
        this.imageDownloader = imageDownloader;
        this.resourceProvider = resourceProvider;
        this.shareCompositeDisposable = new CompositeDisposable();
        this.schedulerProvider = schedulerProvider;
        createShareDelegates();
    }

    void setImage(Image image) {
        this.image = image;
        isFavorite = favoriteImageRepository.isFavorite(image);
    }

    void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    void setImageView(ImageView imageView) {
        this.imageView = imageView;
    }

    @DimenRes
    int getInfoViewHeightRes() {
        return R.dimen.height_small;
    }

    private void createShareDelegates() {
        shareLinkDelegate = shareDelegateFactory.create(ShareDelegate.Mode.LINK);
        copyLinkDelegate = shareDelegateFactory.create(ShareDelegate.Mode.COPY_LINK);
        setAsShareDelegate = shareDelegateFactory.create(ShareDelegate.Mode.SET_AS);
    }

    int getFavoriteButtonIcon() {
        return isFavorite ?
                R.drawable.ic_favorite_black_24dp :
                R.drawable.ic_favorite_border_black_24dp;
    }

    void onFavoriteButtonClicked() {
        if (isFavorite) {
            favoriteImageRepository.unfavorite(image);
        } else {
            favoriteImageRepository.favorite(image);
        }
        isFavorite = !isFavorite;
        if (eventListener != null) {
            eventListener.onFavoriteStateChanged();
        }
    }

    void onSetAsButtonClicked(Context context) {
        if (eventListener != null) {
            eventListener.onSetAsShareStarted();
        }
        setAsShareDelegate.share(context, image)
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable disposable) {
                        shareCompositeDisposable.add(disposable);
                    }

                    @Override
                    public void onComplete() {
                        if (eventListener != null) {
                            eventListener.onSetAsShareFinished();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        if (eventListener != null) {
                            eventListener.onSetAsShareFinished();
                        }
                    }
                });
    }

    void onActivityDestroyed() {
        this.imageView = null;
        this.eventListener = null;
        disposeDisposables();
    }

    void onActivityReady(Context context) {
        downloadImage(context);
        downloadImageDetails();
    }

    private void downloadImageDetails() {
        imageService.getImage(image.getType(), image.getId().value())
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.mainThread())
                .subscribe(new SingleObserver<Image>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable disposable) {
                        imageInfoDisposable = disposable;
                    }

                    @Override
                    public void onSuccess(@NonNull Image image) {
                        ImageActivityModel.this.image = image;
                        isImageInfoLoaded = true;
                        if (eventListener != null) {
                            eventListener.onImageInfoDownloaded();
                        }
                        if (isViewReady() && eventListener != null) {
                            eventListener.onViewReady();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        onSuccess(ImageActivityModel.this.image);
                    }
                });
    }

    private void disposeDisposables() {
        if (imageInfoDisposable != null && !imageInfoDisposable.isDisposed()) {
            imageInfoDisposable.dispose();
            imageInfoDisposable = null;
        }
        if (imageDrawableDisposable != null && !imageDrawableDisposable.isDisposed()) {
            imageDrawableDisposable.dispose();
            imageDrawableDisposable = null;
        }
        shareCompositeDisposable.dispose();
        shareCompositeDisposable.clear();
    }

    private void downloadImage(Context context) {
        imageDownloader.asDrawable(
                context,
                image.getUrl(deviceInfo.getDeviceResolution().getWidth()),
                Priority.IMMEDIATE,
                imageView
        )
                .subscribeOn(schedulerProvider.mainThread())
                .observeOn(schedulerProvider.mainThread())
                .subscribe(new SingleObserver<Drawable>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable disposable) {
                        imageDrawableDisposable = disposable;
                    }

                    @Override
                    public void onSuccess(@NonNull Drawable drawable) {
                        isImageLoaded = true;
                        if (eventListener != null) {
                            eventListener.onImageDownloaded(drawable);
                        }
                        if (isViewReady() && eventListener != null) {
                            eventListener.onViewReady();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        LogWrapper.i(TAG, "Error in downloading image", throwable);
                        if (eventListener != null) {
                            eventListener.onImageDownloadFailed();
                        }
                    }
                });
    }

    private boolean isViewReady() {
        return isImageLoaded && isImageInfoLoaded;
    }

    String getImageTitle() {
        return image.getTitle() != null ? image.getTitle().value() : null;
    }

    @DimenRes
    int getSubtitleTextLeftMargin() {
        return R.dimen.spacing_xl;
    }

    private String getUserLink() {
        String userLink = null;
        if (getUserPortfolioUrl() != null) {
            userLink = getUserPortfolioUrl();
        } else if (getUserProfileUrl() != null) {
            userLink = getUserProfileUrl();
        }
        return userLink;
    }

    private String getUserProfileUrl() {
        String userProfileUrl = null;
        if (image.getUser() != null && image.getUser().getProfileUrl() != null &&
                image.getUser().getProfileUrl().isValid()) {
            userProfileUrl = image.getUser().getProfileUrl().value();
        }
        return userProfileUrl;
    }

    private String getUserPortfolioUrl() {
        String userPortfolioUrl = null;
        if (image.getUser() != null && image.getUser().getProfileUrl() != null &&
                image.getUser().getProfileUrl().isValid()) {
            userPortfolioUrl = image.getUser().getProfileUrl().value();
        }
        return userPortfolioUrl;
    }

    CharSequence getImageSubtitle() {
        final User user = image.getUser();
        String serviceName = image.getService().getName().value();
        String metaString;
        SpannableString spannableString;
        if (user != null && user.getName() != null && user.getName().value() != null) {
            String userName = user.getName().value();
            metaString = resourceProvider.getString(
                    R.string.title_photo_by,
                    user.getName().value(),
                    image.getService().getName().value()
            );
            spannableString = new SpannableString(metaString);

            if (getUserLink() != null) {
                int indexUserNameStart = metaString.indexOf(userName);
                int indexUserNameEnd = indexUserNameStart + userName.length();
                spannableString.setSpan(
                        new ClickableSpan() {
                            @Override
                            public void onClick(android.view.View widget) {
                                if (eventListener != null) {
                                    eventListener.onUserNameClicked(getUserLink());
                                }
                            }
                        },
                        indexUserNameStart,
                        indexUserNameEnd,
                        0
                );
            }
        } else {
            metaString = resourceProvider.getString(
                    R.string.title_photo_by_only_service_name,
                    serviceName
            );
            spannableString = new SpannableString(metaString);
        }

        if (image.getService().getUrl() != null && image.getService().getUrl().isValid()) {
            final String serviceUrl = image.getService().getUrl().value();
            int indexServiceNameStart = metaString.indexOf(serviceName);
            int indexServiceNameEnd = indexServiceNameStart + serviceName.length();
            spannableString.setSpan(
                    new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            if (eventListener != null) {
                                eventListener.onServiceNameClicked(serviceUrl);
                            }

                        }
                    },
                    indexServiceNameStart,
                    indexServiceNameEnd,
                    0
            );
        }
        return spannableString;
    }

    List<InfoView.Info> getImageInfoList() {
        return imageInfoBuilder.buildInfoList(image);
    }

    int getImageInfoColumnCount() {
        return deviceInfo.getNumberOfImageInfoColumns();
    }

    void onShareImageLinkClicked(Context context) {
        shareLinkDelegate.share(context, image)
                .subscribeOn(schedulerProvider.io())
                .observeOn(schedulerProvider.mainThread())
                .subscribe(createShareCompletableObserver());
    }

    void onCopyImageLinkClicked(Context context) {
        copyLinkDelegate.share(context, image)
                .subscribeOn(schedulerProvider.mainThread())
                .observeOn(schedulerProvider.mainThread())
                .subscribe(createShareCompletableObserver());
    }

    private CompletableObserver createShareCompletableObserver() {
        return new CompletableObserver() {
            @Override
            public void onSubscribe(@NonNull Disposable disposable) {
                shareCompositeDisposable.add(disposable);
                if (eventListener != null) {
                    eventListener.onImageShareStarted();
                }
            }

            @Override
            public void onComplete() {
                if (eventListener != null) {
                    eventListener.onImageShareFinished();
                }
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                if (eventListener != null) {
                    eventListener.onImageShareFinished();
                }
            }
        };
    }

    interface EventListener {

        void onFavoriteStateChanged();

        void onSetAsShareStarted();

        void onSetAsShareFinished();

        void onViewReady();

        void onImageDownloaded(Drawable image);

        void onImageInfoDownloaded();

        void onImageDownloadFailed();

        void onUserNameClicked(String userUrl);

        void onServiceNameClicked(String serviceUrl);

        void onImageShareStarted();

        void onImageShareFinished();
    }

}
