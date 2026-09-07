package com.mangotv.app.ui.detail

import com.mangotv.app.data.model.Content

// A tiny hand-off slot for the Content object the user just clicked, keyed
// by id -- lets DetailViewModel render the backdrop/poster/title it already
// has client-side (from whatever catalog row/search/grid the user clicked
// from) immediately, instead of waiting on the full getDetails() JSON round
// trip before even starting the (often large, slow) backdrop image fetch.
// Entries are consumed (removed) once DetailViewModel reads them, so this
// never grows unbounded or serves stale data on a later visit to the same
// title.
object PendingDetailCache {
    private val pending = mutableMapOf<String, Content>()

    fun stash(content: Content) {
        pending[content.id] = content
    }

    fun consume(id: String): Content? = pending.remove(id)
}
