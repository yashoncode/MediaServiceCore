package com.liskovsoft.mediaserviceinterfaces;

import com.liskovsoft.mediaserviceinterfaces.data.CommunityPostPage;
import io.reactivex.rxjava3.core.Observable;

/** Public, read-only channel posts. Neither method requires a signed-in account. */
public interface CommunityService {
    Observable<CommunityPostPage> getChannelPostsObserve(String channelId);
    Observable<CommunityPostPage> continueChannelPostsObserve(String continuation);
    /** Like or unlike a post with one of its action tokens. Needs the web sign-in. */
    Observable<Void> postActionObserve(String action);
}
