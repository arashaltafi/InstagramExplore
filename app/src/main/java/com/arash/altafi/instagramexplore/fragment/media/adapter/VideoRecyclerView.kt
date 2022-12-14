package com.arash.altafi.instagramexplore.fragment.media.adapter

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arash.altafi.instagramexplore.utils.Utils.getScreenHeight
import com.arash.altafi.instagramexplore.utils.Utils.getScreenWidth
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

@ExperimentalCoroutinesApi
class VideoRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var currentVideoHolder: VideoPlayerEventListener? = null

    private val playerVideo = ExoPlayer.Builder(context.applicationContext).build()
    private var videoItemHeight = 0
    private var screenHeight = 0

    init {
        with(playerVideo) {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) currentVideoHolder?.onPlay()
                }
            })
        }

        // FIXME: use correct coroutine dispatcher
        videoViewHolderChanges()
            .onEach { playVideo(it) }
            .launchIn((context as LifecycleOwner).lifecycleScope)

        addOnChildAttachStateChangeListener(object : OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {}

            override fun onChildViewDetachedFromWindow(view: View) {
                val holder = findContainingViewHolder(view)
                if (holder is VideoPlayerEventListener) {
                    holder.onPlayCanceled()
                }
            }
        })
        videoItemHeight = getScreenWidth()
        screenHeight = getScreenHeight()
    }


    fun changePlayingState(play: Boolean) {
        if (play) {
            playerVideo.play()
        } else {
            playerVideo.pause()
        }
    }


    private fun playVideo(targetViewHolder: VideoPlayerEventListener?) {
        if (currentVideoHolder != null && currentVideoHolder == targetViewHolder) return
        try {
            currentVideoHolder?.onPlayCanceled()
            currentVideoHolder = targetViewHolder
            currentVideoHolder?.onPrePlay(playerVideo)
        } catch (throwable: Throwable) {
            // ignore
        }
    }

    private fun videoViewHolderChanges(): Flow<VideoPlayerEventListener?> {
        return callbackFlow {
            val listener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    trySend(dy).isSuccess
                }
            }
            addOnScrollListener(listener)
            awaitClose {
                removeOnScrollListener(listener)
            }
        }
            .buffer(Channel.CONFLATED)
            .mapLatest { getTargetVideoHolder() }
            .flowOn(IO)
    }

    private fun getTargetVideoHolder(): VideoPlayerEventListener? {
        try {
            val position = findCurrentVideoPosition()
            if (position == NO_POSITION) return null
            val viewHolder = findViewHolderForAdapterPosition(position)
            return if (viewHolder is VideoPlayerEventListener) viewHolder else null
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            return null
        }
    }

    private fun findCurrentVideoPosition(): Int {
        var result = NO_POSITION
        val linearLayoutManager = layoutManager as LinearLayoutManager
        val discoverAdapter = adapter as VideoAdapter
        val firstPosition = linearLayoutManager.findFirstVisibleItemPosition()
        val lastPosition = linearLayoutManager.findLastVisibleItemPosition()
        var percentMax = 0
        for (position in firstPosition..lastPosition) {
            if (!discoverAdapter.isItemVideo(position)) continue
            val percent = getVisibleVideoHeight(position, linearLayoutManager)
            if (percentMax < percent) {
                percentMax = percent
                result = position
            }
        }
        return result
    }

    private fun getVisibleVideoHeight(
        position: Int,
        linearLayoutManager: LinearLayoutManager
    ): Int {
        val child = linearLayoutManager.findViewByPosition(position) ?: return NO_POSITION
        val location = IntArray(2)
        child.getLocationInWindow(location)
        return if (location[1] < 0) location[1] + videoItemHeight else screenHeight - location[1]
    }

    private fun VideoAdapter.isItemVideo(position: Int): Boolean {
        return position != NO_POSITION && getItemViewType(position) == VideoAdapter.TYPE_VIDEO
                || getItemViewType(position) == VideoAdapter.TYPE_MUSIC
    }
}

interface VideoPlayerEventListener {
    fun onPrePlay(player: ExoPlayer)
    fun onPlayCanceled()
    fun onPlay()
}