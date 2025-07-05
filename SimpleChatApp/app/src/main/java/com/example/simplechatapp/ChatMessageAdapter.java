package com.example.simplechatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List; // Changed from ArrayList to List for better abstraction

/**
 * ChatMessageAdapter is a {@link RecyclerView.Adapter} that provides data for the chat messages
 * displayed in a {@link RecyclerView}. It takes a list of message strings and binds them
 * to individual views (defined in `item_chat_message.xml`).
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {

    private final List<String> messages; // Use List interface for better flexibility

    /**
     * Constructs a ChatMessageAdapter.
     * @param messages A list of strings, where each string is a chat message to be displayed.
     *                 This list will be used as the data source for the adapter.
     */
    public ChatMessageAdapter(ArrayList<String> messages) { // Constructor can still take ArrayList for convenience
        this.messages = messages;
    }

    /**
     * Called when RecyclerView needs a new {@link ViewHolder} of the given type to represent an item.
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML layout file.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to
     *               an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View of the given view type.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout (item_chat_message.xml) for each chat message item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Called by RecyclerView to display the data at the specified position. This method should
     * update the contents of the {@link ViewHolder#itemView} to reflect the item at the given
     * position.
     *
     * @param holder The ViewHolder which should be updated to represent the contents of the
     *               item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Get the message string at the current position
        String message = messages.get(position);
        // Set the text of the TextView in the ViewHolder to this message
        holder.messageTextView.setText(message);

        // --- Potential Future Enhancements ---
        // 1. Differentiate Sent vs. Received Messages:
        //    - If the message object contained sender information (e.g., "Me" or "Device"),
        //      you could change the background drawable of holder.messageTextView:
        //      e.g., if (message.isSentByMe()) {
        //                holder.messageTextView.setBackgroundResource(R.drawable.message_bubble_sent);
        //                ((LinearLayout.LayoutParams) holder.messageTextView.getLayoutParams()).gravity = Gravity.END;
        //            } else {
        //                holder.messageTextView.setBackgroundResource(R.drawable.message_bubble_received);
        //                ((LinearLayout.LayoutParams) holder.messageTextView.getLayoutParams()).gravity = Gravity.START;
        //            }
        //    - This would require `item_chat_message.xml`'s root to be a LinearLayout with width `match_parent`
        //      and the TextView within it to have width `wrap_content`.
        //
        // 2. Timestamps:
        //    - If messages had timestamps, you could display them in another TextView within the item layout.
        //
        // 3. Rich Content:
        //    - For images or other media, `viewType` in `onCreateViewHolder` would be used to inflate
        //      different layouts, and `getItemViewType(int position)` would need to be overridden.
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of messages in the list.
     */
    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * ViewHolder class for chat messages.
     * Each ViewHolder instance holds a reference to the views within the `item_chat_message.xml` layout.
     * In this case, it's just a single {@link TextView} to display the message text.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView; // TextView to display the chat message content

        /**
         * Constructor for the ViewHolder.
         * @param itemView The View inflated from `item_chat_message.xml`, representing a single chat item.
         */
        ViewHolder(View itemView) {
            super(itemView);
            // Find the TextView within the item view by its ID
            messageTextView = itemView.findViewById(R.id.message_text_view);
        }
    }
}
