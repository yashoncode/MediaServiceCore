package com.liskovsoft.mediaserviceinterfaces.data;

/** A successful send may omit the renderer; a chat rule failure carries YouTube's own text. */
public final class LiveChatMessageResult {
    private final ChatItem mItem;
    private final String mError;

    public LiveChatMessageResult(ChatItem item, String error) {
        mItem = item;
        mError = error;
    }

    public ChatItem getItem() { return mItem; }
    public String getError() { return mError; }
    public boolean isSuccess() { return mError == null; }
}
