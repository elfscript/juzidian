/*
 * Copyright Nathan Jones 2013
 *
 * This file is part of Juzidian.
 *
 * Juzidian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Juzidian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Juzidian.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.juzidian.core;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A cancellable, pending {@link SearchResults}.
 */
public class SearchResultsFuture {

	private final Future<SearchResults> results;

	private final SearchCanceller canceller;

	public SearchResultsFuture(final Future<SearchResults> results, final SearchCanceller canceller) {
		this.results = results;
		this.canceller = canceller;
	}

	/**
	 * Get the {@link SearchResults} for this future.
	 * <p>
	 * This operation will block until the results become available.
	 * 
	 * @return a {@link SearchResults}.
	 * @throws SearchCancelledException if {@link #cancel()} is invoked while
	 *         this operation is in progress.
	 */
	public SearchResults getResults() throws SearchCancelledException {
		try {
			return results.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof DictionaryDataStoreQueryCancelledException) {
				throw new SearchCancelledException(e);
			}
			throw new RuntimeException("Failed to get search results", e);
		}
	}

	/**
	 * Cancel the search.
	 */
	public void cancel() {
		canceller.cancel();
	}

}
