/*
 * Copyright 2011 Witoslaw Koczewsi <wi@koczewski.de>, Artjom Kochtchi
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package ilarkesto.navig.plugins;

import ilarkesto.core.navig.Item;
import ilarkesto.core.navig.Navigator;
import ilarkesto.core.navig.Plugin;

public class ExitPlugin implements Plugin {

	@Override
	public void initialize(Navigator navigator) {
		navigator.getPage().add(new Item(this, "Exit"));
	}

	@Override
	public void execute(Navigator navigator, Item item) {
		System.exit(0);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}