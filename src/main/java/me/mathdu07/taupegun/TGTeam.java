package me.mathdu07.taupegun;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class TGTeam {
	private String name;
	private String displayName;
	private ChatColor color;
	private TGPlugin plugin;
	private ArrayList<Player> players = new ArrayList<Player>();
	
	public TGTeam(String name, String displayName, ChatColor color, TGPlugin plugin) {
		this.name = name;
		this.displayName = displayName;
		this.color = color;
		this.plugin = plugin;
		
		Scoreboard sb = this.plugin.getScoreboard();
		sb.registerNewTeam(this.name);
	
		Team t = sb.getTeam(this.name);
		t.setDisplayName(this.displayName);
		t.setCanSeeFriendlyInvisibles(true);
		t.setPrefix(this.color+"");
	}
	
	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public ArrayList<Player> getPlayers() {
		return players;
	}

	public void addPlayer(Player playerExact) {
		players.add(playerExact);
		plugin.getScoreboard().getTeam(this.name).addPlayer(playerExact);
	}
	
	public void removePlayer(Player p)
	{
	    players.remove(p);
	    plugin.getScoreboard().getTeam(name).removePlayer(p);
	}

	public void teleportTo(Location lo) {
		for (Player p : players) {
			p.teleport(lo);
		}
	}

	public ChatColor getChatColor() {
		return color;
	}
}
