package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.List;

public final class CommunityPostPage {
    public final List<CommunityPost> posts;
    public final String nextPageToken;

    public CommunityPostPage(List<CommunityPost> posts, String nextPageToken) {
        this.posts = posts;
        this.nextPageToken = nextPageToken;
    }
}
