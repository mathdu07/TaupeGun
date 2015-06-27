package me.mathdu07.taupegun;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class TGPlugin extends JavaPlugin implements ConversationAbandonedListener {

	private Logger logger = null;
	private LinkedList<Location> loc = new LinkedList<Location>();
	private Random random = null;
	private ShapelessRecipe goldenMelon = null;
	private ShapedRecipe compass = null;
	private Integer episode = 0;
	private Boolean gameRunning = false;
	private Scoreboard sb = null;
	private Integer minutesLeft = 0;
	private Integer secondsLeft = 0;
	private NumberFormat formatter = new DecimalFormat("00");
	private String sbobjname = "KTP";
	private Boolean damageIsOn = false;
	private ArrayList<TGTeam> teams = new ArrayList<TGTeam>();
	private HashMap<String, ConversationFactory> cfs = new HashMap<String, ConversationFactory>();
	private TGPrompts tg = null;
	private HashSet<String> deadPlayers = new HashSet<String>();
	private ArrayList<Player> taupes = new ArrayList<Player>();
	private Set<String> taupesClaimed = new HashSet<String>();
	private TGTeam taupeTeam = null;
	
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		 
		File positions = new File("plugins/TaupeGun/positions.txt");
		if (positions.exists()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(positions));
				String line;
				while ((line = br.readLine()) != null) {
					String[] l = line.split(",");
					getLogger().info("Adding position "+Integer.parseInt(l[0])+","+Integer.parseInt(l[1])+" from positions.txt");
					addLocation(Integer.parseInt(l[0]), Integer.parseInt(l[1]));
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try { if (br != null) br.close(); }
				catch (Exception e) { e.printStackTrace(); } //c tré l'inline
			}
			
		}
		tg = new TGPrompts(this);
		logger = Bukkit.getLogger();
		logger.info("TaupeGun loaded");
		random = new Random();
		
		goldenMelon = new ShapelessRecipe(new ItemStack(Material.SPECKLED_MELON));
		goldenMelon.addIngredient(1, Material.GOLD_BLOCK);
		goldenMelon.addIngredient(1, Material.MELON);
		this.getServer().addRecipe(goldenMelon);
		
		if (getConfig().getBoolean("compass")) {
			compass = new ShapedRecipe(new ItemStack(Material.COMPASS));
			compass.shape(new String[] {"CIE", "IRI", "BIF"});
			compass.setIngredient('I', Material.IRON_INGOT);
			compass.setIngredient('R', Material.REDSTONE);
			compass.setIngredient('C', Material.SULPHUR);
			compass.setIngredient('E', Material.SPIDER_EYE);
			compass.setIngredient('B', Material.BONE);
			compass.setIngredient('F', Material.ROTTEN_FLESH);
			this.getServer().addRecipe(compass);
		}
		
		getServer().getPluginManager().registerEvents(new TGPluginListener(this), this);
		
		sb = Bukkit.getServer().getScoreboardManager().getNewScoreboard();
		Objective obj = sb.registerNewObjective("Vie", "health");
		obj.setDisplayName("Vie");
		obj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
		
		setMatchInfo();
		
		getServer().getWorlds().get(0).setGameRuleValue("doDaylightCycle", "false");
		getServer().getWorlds().get(0).setTime(6000L);
		getServer().getWorlds().get(0).setStorm(false);
		getServer().getWorlds().get(0).setDifficulty(Difficulty.HARD);
		
		cfs.put("teamPrompt", new ConversationFactory(this)
		.withModality(true)
		.withFirstPrompt(tg.getTNP())
		.withEscapeSequence("cancel")
		.thatExcludesNonPlayersWithMessage("Il faut être un joueur ingame.")
		.withLocalEcho(false)
		.addConversationAbandonedListener(this));
		
		cfs.put("playerPrompt", new ConversationFactory(this)
		.withModality(true)
		.withFirstPrompt(tg.getPP())
		.withEscapeSequence("cancel")
		.thatExcludesNonPlayersWithMessage("Il faut être un joueur ingame.")
		.withLocalEcho(false)
		.addConversationAbandonedListener(this));
	}
	
	
	public void addLocation(int x, int z) {
		loc.add(new Location(getServer().getWorlds().get(0), x, getServer().getWorlds().get(0).getHighestBlockYAt(x,z)+120, z));
	}
	
	public void setMatchInfo() {
		Objective obj = null;
		try {
			obj = sb.getObjective(sbobjname);
			obj.setDisplaySlot(null);
			obj.unregister();
		} catch (Exception e) {
			
		}
		Random r = new Random();
		sbobjname = "TG"+r.nextInt(10000000);
		obj = sb.registerNewObjective(sbobjname, "dummy");
		obj = sb.getObjective(sbobjname);

		obj.setDisplayName(this.getScoreboardName());
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		obj.getScore(ChatColor.GRAY+"Episode "+ChatColor.WHITE+episode).setScore(5);
		obj.getScore(ChatColor.WHITE+""+Bukkit.getServer().getOnlinePlayers().size()+ChatColor.GRAY+" joueurs").setScore(4);
		obj.getScore(ChatColor.WHITE+""+getAliveTeams().size()+ChatColor.GRAY+" teams").setScore(3);
		obj.getScore("").setScore(2);
		obj.getScore(ChatColor.WHITE+formatter.format(this.minutesLeft)+ChatColor.GRAY+":"+ChatColor.WHITE+formatter.format(this.secondsLeft)).setScore(1);
	}

	private ArrayList<TGTeam> getAliveTeams() {
		ArrayList<TGTeam> aliveTeams = new ArrayList<TGTeam>();
		for (TGTeam t : teams) {
			for (Player p : t.getPlayers()) {
				if (p.isOnline() && !aliveTeams.contains(t)) aliveTeams.add(t);
			}
		}
		return aliveTeams;
	}

	@Override
	public void onDisable() {
		logger.info("TaupeGun unloaded");
	}
	
	public boolean onCommand(final CommandSender s, Command c, String l, String[] a) {
	    
		if (c.getName().equalsIgnoreCase("taupegun")) {
			if (!(s instanceof Player)) {
				s.sendMessage(ChatColor.RED+"Vous devez être un joueur");
				return true;
			}
			Player pl = (Player)s;
			if (!pl.isOp()) {
				pl.sendMessage(ChatColor.RED+"Lolnope.");
				return true;
			}
			if (a.length == 0) {
				//pl.sendMessage("Usage : /uh <start|shift|team|addspawn|generatewalls>");
				return false;
			}
			if (a[0].equalsIgnoreCase("start")) {
				if (teams.size() == 0) {
					for (Player p : getServer().getOnlinePlayers()) {
						TGTeam uht = new TGTeam(p.getName(), p.getName(), ChatColor.WHITE, this);
						uht.addPlayer(p);
						teams.add(uht);
					}
				}
				if (loc.size() < teams.size()) {
					s.sendMessage(ChatColor.RED+"Pas assez de positions de TP");
					return true;
				}
				LinkedList<Location> unusedTP = loc;
				for (final TGTeam t : teams) {
					final Location lo = unusedTP.get(this.random.nextInt(unusedTP.size()));
					new BukkitRunnable() {

						@Override
						public void run() {
							t.teleportTo(lo);
							for (Player p : t.getPlayers()) {
								p.setGameMode(GameMode.SURVIVAL);
								p.setHealth(20);
								p.setFoodLevel(20);
								p.setExhaustion(5F);
								p.getInventory().clear();
								p.getInventory().setArmorContents(new ItemStack[] {new ItemStack(Material.AIR), new ItemStack(Material.AIR), 
										new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
								p.setExp(0L+0F);
								p.setLevel(0);
								p.closeInventory();
								p.getActivePotionEffects().clear();
								p.setCompassTarget(lo);
							}
						}
					}.runTaskLater(this, 10L);
					
					unusedTP.remove(lo);
				}
				
				new BukkitRunnable() {

					@Override
					public void run() {
						damageIsOn = true;
					}
				}.runTaskLater(this, 600L);
				World w = Bukkit.getOnlinePlayers().iterator().next().getWorld();
				w.setGameRuleValue("doDaylightCycle", ((Boolean)getConfig().getBoolean("daylightCycle.do")).toString());
				w.setTime(getConfig().getLong("daylightCycle.time"));
				w.setStorm(false);
				w.setDifficulty(Difficulty.HARD);
				this.episode = 1;
				this.minutesLeft = getEpisodeLength();
				this.secondsLeft = 0;
				
				new BukkitRunnable() {
					@Override
					public void run() {
						setMatchInfo();
						secondsLeft--;
						if (secondsLeft == -1) {
							minutesLeft--;
							secondsLeft = 59;
						}
						if (minutesLeft == -1) {
							minutesLeft = getEpisodeLength();
							secondsLeft = 0;
							Bukkit.getServer().broadcastMessage(ChatColor.AQUA+"-------- Fin episode "+episode+" --------");
							shiftEpisode();
						}
					} 
				}.runTaskTimer(this, 20L, 20L);
				
				new BukkitRunnable() {
					
					@Override
					public void run()
					{
						 for (TGTeam t : teams)
						 {
							 ArrayList<Player> players = t.getPlayers();
						     int taupeId = random.nextInt(players.size());
						     
						     Player taupe = players.get(taupeId);
						     taupes.add(taupe);
						     taupe.sendMessage(ChatColor.RED + "------------------------");
						     taupe.sendMessage(ChatColor.GOLD + "Vous avez été désigné comme taupe !");
						     taupe.sendMessage(ChatColor.GOLD + "Votre objectif est de ruiner votre équipe, et de rejoindre les autre taupes");
						     taupe.sendMessage(ChatColor.GOLD + "Pour cela, vous avez 2 commandes spéciales : ");
						     taupe.sendMessage(ChatColor.GOLD + "/reveal Pour vous révéler, histoire de rigoler ;)");
						     taupe.sendMessage(ChatColor.GOLD + "/claim Pour obtenir un kit (utilisable une fois)");
						     taupe.sendMessage(ChatColor.GOLD + "/t <message> Pour écrire à toutes les taupes,");
						     taupe.sendMessage(ChatColor.GOLD + "les messages restent anonymes tant que vous ne vous êtes pas révéler");
						     taupe.sendMessage(ChatColor.GOLD + "Bonne chance petite taupe, ne te fais pas spot !");
						     taupe.sendMessage(ChatColor.RED + "------------------------");
						 }
					}
					
				}.runTaskLater(this, 20 * getConfig().getInt("taupe-drawing-time"));
				
				Bukkit.getServer().broadcastMessage(ChatColor.GREEN+"--- GO ---");
				this.gameRunning = true;
				return true;
			} else if (a[0].equalsIgnoreCase("shift")) {
				Bukkit.getServer().broadcastMessage(ChatColor.AQUA+"-------- Fin episode "+episode+" [forcé par "+s.getName()+"] --------");
				shiftEpisode();
				this.minutesLeft = getEpisodeLength();
				this.secondsLeft = 0;
				return true;
			} else if (a[0].equalsIgnoreCase("team")) {
				Inventory iv = this.getServer().createInventory(pl, 54, "- Teams -");
				Integer slot = 0;
				ItemStack is = null;
				for (TGTeam t : teams) {
					is = new ItemStack(Material.BEACON, t.getPlayers().size());
					ItemMeta im = is.getItemMeta();
					im.setDisplayName(t.getChatColor()+t.getDisplayName());
					ArrayList<String> lore = new ArrayList<String>();
					for (Player p : t.getPlayers()) {
						lore.add("- "+p.getDisplayName());
					}
					im.setLore(lore);
					is.setItemMeta(im);
					iv.setItem(slot, is);
					slot++;
				}
				
				ItemStack is2 = new ItemStack(Material.DIAMOND);
				ItemMeta im2 = is2.getItemMeta();
				im2.setDisplayName(ChatColor.AQUA+""+ChatColor.ITALIC+"Créer une team");
				is2.setItemMeta(im2);
				iv.setItem(53, is2);
				
				pl.openInventory(iv);
				return true;
//			} else if (a[0].equalsIgnoreCase("newteam")) {
//				if (a.length != 4) {
//					pl.sendMessage(ChatColor.RED+"Usage: /uh newteam nom couleur nom nomAffiché");
//					return true;
//				}
//				if (a[1].length() > 16) {
//					pl.sendMessage(ChatColor.RED+"Le nom de la team ne doit pas faire plus de 16 chars");
//					return true;
//				}
//				if (a[3].length() > 32) {
//					pl.sendMessage(ChatColor.RED+"Le nom affiché de la team ne doit pas faire plus de 32 chars");
//				}
//				ChatColor cc;
//				try {
//					cc = ChatColor.valueOf(a[2].toUpperCase());
//				} catch (IllegalArgumentException e) {
//					pl.sendMessage(ChatColor.RED+"La couleur est invalide.");
//					return true;
//				}
//				teams.add(new UHTeam(a[1], a[3], cc, this));
//				pl.sendMessage(ChatColor.GREEN+"Team créée. Utilisez /uh playertoteam "+a[1]+" nomjoueur pour y ajouter des joueurs.");
//				return true;
//			} else if (a[0].equalsIgnoreCase("playertoteam")) {
//				if (a.length != 3) {
//					pl.sendMessage(ChatColor.RED+"Usage: /uh playertoteam nomteam nomjoueur");
//					return true;
//				}
//				UHTeam t = getTeam(a[1]);
//				if (t == null) {
//					pl.sendMessage(ChatColor.RED+"Team inexistante. /uh teams pour voir les teams");
//					return true;
//				}
//				if (Bukkit.getPlayerExact(a[2]) == null) {
//					pl.sendMessage(ChatColor.RED+"Le joueur est introuvable. (Il doit être connecté.)");
//					return true;
//				}
//				t.addPlayer(Bukkit.getPlayerExact(a[2]));
//				pl.sendMessage(ChatColor.GREEN+Bukkit.getPlayerExact(a[2]).getName()+" ajouté à la team "+a[1]+".");
//				return true;
//			} else if (a[0].equalsIgnoreCase("teams")) {
//				for (UHTeam t : teams) {
//					pl.sendMessage(ChatColor.DARK_GRAY+"- "+ChatColor.AQUA+t.getName()+ChatColor.DARK_GRAY+" ["+ChatColor.GRAY+t.getDisplayName()+ChatColor.DARK_GRAY+"] - "+ChatColor.GRAY+t.getPlayers().size()+ChatColor.DARK_GRAY+" joueurs");
//				}
//				return true;
			} else if (a[0].equalsIgnoreCase("addspawn")) {
				addLocation(pl.getLocation().getBlockX(), pl.getLocation().getBlockZ());
				pl.sendMessage(ChatColor.DARK_GRAY+"Position ajoutée: "+ChatColor.GRAY+pl.getLocation().getBlockX()+","+pl.getLocation().getBlockZ());
				return true;
			} else if (a[0].equalsIgnoreCase("generateWalls")) {
				pl.sendMessage(ChatColor.GRAY+"Génération en cours...");
				try {
					Integer halfMapSize = (int) Math.floor(this.getConfig().getInt("map.size")/2);
					Integer wallHeight = this.getConfig().getInt("map.wall.height");
					Material wallBlock = Material.getMaterial(this.getConfig().getInt("map.wall.block"));
					World w = pl.getWorld();
					
					Location spawn = w.getSpawnLocation();
					Integer limitXInf = spawn.add(-halfMapSize, 0, 0).getBlockX();
					
					spawn = w.getSpawnLocation();
					Integer limitXSup = spawn.add(halfMapSize, 0, 0).getBlockX();
					
					spawn = w.getSpawnLocation();
					Integer limitZInf = spawn.add(0, 0, -halfMapSize).getBlockZ();
					
					spawn = w.getSpawnLocation();
					Integer limitZSup = spawn.add(0, 0, halfMapSize).getBlockZ();
					
					for (Integer x = limitXInf; x <= limitXSup; x++) {
						w.getBlockAt(x, 1, limitZInf).setType(Material.BEDROCK);
						w.getBlockAt(x, 1, limitZSup).setType(Material.BEDROCK);
						for (Integer y = 2; y <= wallHeight; y++) {
							w.getBlockAt(x, y, limitZInf).setType(wallBlock);
							w.getBlockAt(x, y, limitZSup).setType(wallBlock);
						}
					} 
					
					for (Integer z = limitZInf; z <= limitZSup; z++) {
						w.getBlockAt(limitXInf, 1, z).setType(Material.BEDROCK);
						w.getBlockAt(limitXSup, 1, z).setType(Material.BEDROCK);
						for (Integer y = 2; y <= wallHeight; y++) {
							w.getBlockAt(limitXInf, y, z).setType(wallBlock);
							w.getBlockAt(limitXSup, y, z).setType(wallBlock);
						}
					} 
				} catch (Exception e) {
					e.printStackTrace();
					pl.sendMessage(ChatColor.RED+"Echec génération. Voir console pour détails.");
					return true;
				}
				pl.sendMessage(ChatColor.GRAY+"Génération terminée.");
				return true;
			}
		}
		else if (c.getName().equalsIgnoreCase("t"))
		{
		    if (!(s instanceof Player))
		    {
		        s.sendMessage(ChatColor.RED + "Vous devez être un joueur pour utiliser cette commande.");
		        return true;
		    }
		    
		    Player p = (Player) s;
		    
		    if (isTaupe(p))
		    {
		        if (a.length == 0)
		            return false;
		        
		        sendMessageToTaupes(p, join(a, " "));
		    }
		    else
		    {
		        p.sendMessage(ChatColor.RED + "Tu n'es pas une taupe !");
		    }
		    
		    return true;
		}
		else if (c.getName().equalsIgnoreCase("reveal"))
		{
		    if (!(s instanceof Player))
            {
                s.sendMessage(ChatColor.RED + "Vous devez être un joueur pour utiliser cette commande.");
                return true;
            }
            
            Player p = (Player) s;
            
            if (isTaupe(p))
            {
                if (isTaupeRevealed(p))
                {
                    p.sendMessage(ChatColor.RED + "Vous êtes déjà révélé");
                    return true;
                }
                
                if (taupeTeam == null)
                {
                    taupeTeam = new TGTeam("Taupes", "Taupes", ChatColor.RED, this);
                }
                
                TGTeam oldTeam = getTeamForPlayer(p);
                oldTeam.removePlayer(p);
                taupeTeam.addPlayer(p);
                
                // Give golden apple
                p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
                
                getServer().broadcastMessage(ChatColor.GOLD + "--- " + p.getName() + " se révèle être une taupe ! ---");
                for (Player pp : getServer().getOnlinePlayers())
                {
                    pp.playSound(pp.getLocation(), Sound.GHAST_SCREAM, 1f, 1f);
                }
            }
            else
            {
                p.sendMessage(ChatColor.RED + "Tu n'es pas une taupe !");
            }
            
            return true;
		}
		else if (c.getName().equalsIgnoreCase("claim"))
		{
			if (!(s instanceof Player))
            {
                s.sendMessage(ChatColor.RED + "Vous devez être un joueur pour utiliser cette commande.");
                return true;
            }
			
			Player p = (Player) s;
			
			if (isTaupe(p))
			{
				if (!taupesClaimed.contains(p.getName()))
				{
					// Give kit
					Collection<ItemStack> kit = new ArrayList<ItemStack>();
					kit.add(new ItemStack(Material.FLINT_AND_STEEL));
					kit.add(new ItemStack(Material.TNT, 2));
					ItemStack poisonPotion = new ItemStack(Material.POTION);
					poisonPotion.setDurability((short) 16388); // Splash Potion of poison I
					kit.add(poisonPotion);
					ItemStack healingPotion = new ItemStack(Material.POTION);
					healingPotion.setDurability((short) 8197); // Potion of healing I
					kit.add(healingPotion);
					
					p.getInventory().addItem(kit.toArray(new ItemStack[kit.size()]));
					taupesClaimed.add(p.getName());
					
					p.sendMessage(ChatColor.GREEN + "Le kit vous a été donné");
				}
				else
				{
					p.sendMessage(ChatColor.RED + "Vous avez déjà réclamé votre kit");
				}
			}
			else
			{
				p.sendMessage(ChatColor.RED + "Tu n'es pas une taupe !");
			}
			
			return true;
		}
		
		return false;
	}
	
	public void shiftEpisode() {
		this.episode++;
	}
	
	public boolean isGameRunning() {
		return this.gameRunning;
	}

	public void updatePlayerListName(Player p) {
		p.setScoreboard(sb);
	}

	public void addToScoreboard(Player player) {
		player.setScoreboard(sb);
	}

	public boolean isTakingDamage() {
		return damageIsOn;
	}
	
	public Scoreboard getScoreboard() {
		return sb;
	}
	
	public TGTeam getTeam(String name) {
		for(TGTeam t : teams) {
			if (t.getName().equalsIgnoreCase(name)) return t;
		}
		return null;
	}

	public TGTeam getTeamForPlayer(Player p) {
		for(TGTeam t : teams) {
			if (t.getPlayers().contains(p)) return t;
		}
		return null;
	}
	
	public Integer getEpisodeLength() {
		return this.getConfig().getInt("episodeLength");
	}

	@Override
	public void conversationAbandoned(ConversationAbandonedEvent abandonedEvent) {
		if (!abandonedEvent.gracefulExit()) {
			abandonedEvent.getContext().getForWhom().sendRawMessage(ChatColor.RED+"Abandonné par "+abandonedEvent.getCanceller().getClass().getName());
		}		
	}
	
	public boolean createTeam(String name, ChatColor color) {
		if (teams.size() <= 50) {
			teams.add(new TGTeam(name, name, color, this));
			return true;
		}
		return false;
	}
	public ConversationFactory getConversationFactory(String string) {
		if (cfs.containsKey(string)) return cfs.get(string);
		return null;
	}
	
	public boolean isPlayerDead(String name) {
		return deadPlayers.contains(name);
	}
	
	public void addDead(String name) {
		deadPlayers.add(name);
	}
	
	public String getScoreboardName() {
		String s = this.getConfig().getString("scoreboard", "Taupe Gun");
		return s.substring(0, Math.min(s.length(), 16));
	}

	public boolean inSameTeam(Player pl, Player pl2) {
		return (getTeamForPlayer(pl).equals(getTeamForPlayer(pl2)));
	}
	
	public ArrayList<Player> getTaupes()
	{	    
	    return taupes;
	}
	
	public boolean isTaupe(Player p)
	{
	    return getTaupes().contains(p);
	}
	
	public boolean isTaupeRevealed(Player p)
	{
	    if (taupeTeam == null)
	        return false;
	    
	    else
	        return taupeTeam.getPlayers().contains(p);
	}
	
	public void sendMessageToTaupes(Player taupe, String msg)
	{
	    if (!isTaupe(taupe))
	        return;
	    
	    int teamId = teams.indexOf(getTeamForPlayer(taupe));
	    String msgFinal = ChatColor.GOLD + "[Taupes] " + (isTaupeRevealed(taupe)
	            ? ChatColor.RED + "<" + taupe.getName() + "> "
	            : ChatColor.RED + "<???(" + (teamId+1) + ")> ")
	            + ChatColor.RESET + msg;
	    
	    for (Player p : getTaupes())
	    {
	        p.sendMessage(msgFinal);
	    }
	}
	
	public static String join(String[] array, String gap)
	{
	    if (array.length == 0)
	        return "";
	    
	    String result = array[0];
	    
	    for (int i = 1; i < array.length; i++)
	    {
	        result += gap + array[i];
	    }
	    
	    return result;
	}
}
