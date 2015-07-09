/*
 * Copyright 2011 Witoslaw Koczewsi <wi@koczewski.de>, Artjom Kochtchi
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package ilarkesto.persistence;

import java.util.Collection;

public abstract class ADatobManager<D extends Datob> {

	/**
	 * Called by the datob, when it is modified
	 */
	public abstract void onDatobModified(D datob, String field, String value);

	public abstract void onMissingMaster(D datob);

	public abstract void updateLastModified(D datob);

	public abstract void ensureIntegrityOfStructures(Collection<D> views);

}
