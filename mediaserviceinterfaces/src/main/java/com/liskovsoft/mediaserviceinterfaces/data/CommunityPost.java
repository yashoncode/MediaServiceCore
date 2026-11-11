package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.List;

/** Display data only. Counts and times retain YouTube's localized text. Optional values are null. */
public final class CommunityPost {
    public final String id;
    public final String author;
    public final String authorChannelId;
    public final String avatarUrl;
    public final String timeText;
    public final List<TextRun> text;
    public final String likeCountText;
    public final String replyCountText;
    public final Attachment attachment;
    /** Opens the post's comments (CommentsService key "post:detail:" + this). */
    public final String commentsParams;
    /** Present only when the page was fetched with the web sign-in. */
    public final boolean liked;
    public final String likeAction;
    public final String unlikeAction;

    public CommunityPost(String id, String author, String authorChannelId, String avatarUrl,
                         String timeText, List<TextRun> text, String likeCountText,
                         String replyCountText, Attachment attachment, String commentsParams,
                         boolean liked, String likeAction, String unlikeAction) {
        this.id = id;
        this.author = author;
        this.authorChannelId = authorChannelId;
        this.avatarUrl = avatarUrl;
        this.timeText = timeText;
        this.text = text;
        this.likeCountText = likeCountText;
        this.replyCountText = replyCountText;
        this.attachment = attachment;
        this.commentsParams = commentsParams;
        this.liked = liked;
        this.likeAction = likeAction;
        this.unlikeAction = unlikeAction;
    }

    public static final class TextRun {
        public final String text;
        public final String url;
        public final String videoId;
        public final String channelId;

        public TextRun(String text, String url, String videoId, String channelId) {
            this.text = text;
            this.url = url;
            this.videoId = videoId;
            this.channelId = channelId;
        }
    }

    public static final class Image {
        public final String url;
        public final int width;
        public final int height;

        public Image(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    /** Exactly one of images, video or poll is present. */
    public static final class Attachment {
        public final List<Image> images;
        public final AttachedVideo video;
        public final Poll poll;

        public Attachment(List<Image> images, AttachedVideo video, Poll poll) {
            this.images = images;
            this.video = video;
            this.poll = poll;
        }
    }

    public static final class AttachedVideo {
        public final String id;
        public final String title;
        public final String thumbnailUrl;
        public final String lengthText;
        public final String author;

        public AttachedVideo(String id, String title, String thumbnailUrl, String lengthText, String author) {
            this.id = id;
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
            this.lengthText = lengthText;
            this.author = author;
        }
    }

    public static final class Poll {
        public final List<String> choices;
        public final String totalVotesText;

        public Poll(List<String> choices, String totalVotesText) {
            this.choices = choices;
            this.totalVotesText = totalVotesText;
        }
    }
}
