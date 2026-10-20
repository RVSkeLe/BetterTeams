package com.booksaw.betterTeams.integrations.placeholder.provider;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.integrations.placeholder.IndividualTeamPlaceholderProvider;

public class PositionMembersPlaceholderProvider implements IndividualTeamPlaceholderProvider {

	@Override
	public String getPlaceholderForTeam(Team team) {
		return String.valueOf(Team.getTeamManager().getTeamPositionByMembers(team));
	}

}
