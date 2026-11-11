package com.liskovsoft.mediaserviceinterfaces;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.LiveChatMessageResult;
import io.reactivex.rxjava3.core.Observable;

public interface LiveChatService {
    Observable<ChatItem> openLiveChatObserve(String chatKey);
    Observable<LiveChatMessageResult> sendLiveChatMessageObserve(
            String videoId, String channelId, String text);
}
