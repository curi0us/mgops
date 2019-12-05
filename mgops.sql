SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;


CREATE TABLE `connections` (
  `id` int(11) UNSIGNED NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL,
  `remote` varchar(16) NOT NULL,
  `remote_port` int(11) NOT NULL,
  `local` varchar(16) NOT NULL,
  `local_port` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `games` (
  `id` int(10) UNSIGNED NOT NULL,
  `lobby` smallint(5) UNSIGNED NOT NULL,
  `name` text NOT NULL,
  `password` text NOT NULL,
  `description` text NOT NULL,
  `host_id` int(10) UNSIGNED NOT NULL,
  `rule` tinyint(3) UNSIGNED NOT NULL,
  `map` tinyint(3) UNSIGNED NOT NULL,
  `players` tinyint(3) UNSIGNED NOT NULL,
  `max_players` tinyint(3) UNSIGNED NOT NULL,
  `vs_rating` int(10) UNSIGNED NOT NULL,
  `vs_restriction_type` tinyint(3) UNSIGNED NOT NULL,
  `weapon_restriction` tinyint(3) UNSIGNED NOT NULL,
  `bitmask_options_one` tinyint(3) UNSIGNED NOT NULL,
  `bitmask_options_two` tinyint(3) UNSIGNED NOT NULL,
  `host_only` tinyint(3) UNSIGNED NOT NULL,
  `ping` int(10) UNSIGNED NOT NULL,
  `misc_data` text
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `lists` (
  `id` int(11) NOT NULL,
  `type` int(11) NOT NULL,
  `owner_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

CREATE TABLE `lobbies` (
  `id` int(10) UNSIGNED NOT NULL,
  `type` tinyint(3) UNSIGNED NOT NULL,
  `name` varchar(16) NOT NULL,
  `ip` varchar(15) NOT NULL,
  `port` smallint(5) UNSIGNED NOT NULL,
  `players` smallint(5) UNSIGNED NOT NULL DEFAULT '0',
  `trusted` smallint(5) UNSIGNED NOT NULL DEFAULT '0'
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

INSERT INTO `lobbies` (`id`, `type`, `name`, `ip`, `port`, `players`, `trusted`) VALUES
(1, 0, 'mg3-gate-eu', '192.3.217.162', 5731, 0, 0),
(2, 1, 'mg3-account-eu', '192.3.217.162', 5731, 0, 0),
(12, 2, 'REX (NTSC-U)', '192.3.217.162', 5731, 0, 0),
(18, 2, 'RAY (PAL)', '192.3.217.162', 5732, 0, 0),
(19, 2, 'TX-55 (NTSC-J)', '192.3.217.162', 5733, 0, 0),
(27, 2, 'Shagohod (Dev)', '75.127.3.188', 5188, 1, 0);

CREATE TABLE `news` (
  `id` int(10) UNSIGNED NOT NULL,
  `visible` tinyint(1) NOT NULL DEFAULT '1',
  `topic` varchar(64) NOT NULL,
  `message` varchar(1024) NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

INSERT INTO `news` (`id`, `visible`, `topic`, `message`, `time`) VALUES
(1, 0, 'Welcome Back!', 'Welcome back everyone!\\n\\nMost functionality is working but there are a couple features that do not. Adding people to your black list does not work at all. Friends list works but if you do it in game it will not be reflected in game until you quit and rejoin the game.\\n\\nYou cannot view user stats in game but snake.savemgo.com does track stats and they can be viewed from there.\\n\\nThere is also no access to Score Rankings or VS. Rating Rankings except on snake.savemgo.com', '2013-10-04 21:10:54'),
(3, 0, 'BUGS BUGS BUGS', 'Sorry everyone. As you are on the server I occasionally need to fix urgent bugs. To do this I need to restart servers so you may get disconnected mid-game. This is my doing not a problem with the server. Ideally once the critical issues are fixed this will happen on a fixed scedule so its predictable. For now please bear with it.', '2013-10-05 18:34:32'),
(5, 0, 'Server Move', 'The server has been moved as such the DNS IP has changed.\\nPrimary:192.3.27.145\\nSecondary:172.245.26.30\\n\\nThe RAY (PAL) server has also been taken down. PAL players can create games on REX and just tag them with [PAL]', '2013-12-27 00:42:09'),
(6, 0, 'New Host', 'Due to all the disconnects and random issues lately with the last host I\'ve setup a new host. Again this means IPs need to be changed, hopefully for the last time.\\n\\nPrimary DNS IP: 192.3.157.163\\nSecondary DNS IP: 192.3.157.164', '2014-01-10 00:26:28'),
(7, 1, 'Skype Group', 'We have a Skype group which most players are on. Add me Matthew \'envisi0n.\' on Skype and I will add you to the main group', '2014-03-25 06:54:34'),
(8, 0, 'Weekly Game', 'I will be hosting weekly games every Wednesday at 2000 and Saturday at 1600 Eastern Time(New York/Boston). So if you are looking for a game be there.', '2014-03-25 07:02:00'),
(9, 0, 'test', 'test', '0000-00-00 00:00:00'),
(10, 1, 'Discord Server', 'So, you\'re probably wonder if there are ever any players on...the answer is yes. Usually on weeks you\'ll find a game or two. Its not much but it is something.\\nIf you\'re looking chat with other players there is the skype group but I\'d now prefer is people checkout our Discord server, https://discordapp.com/invite/0esdR5uckLvRIWvB', '2016-02-23 01:03:40');

CREATE TABLE `players` (
  `id` int(10) UNSIGNED NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL,
  `lobby` smallint(5) UNSIGNED NOT NULL,
  `game` int(10) UNSIGNED NOT NULL,
  `game_team` tinyint(10) NOT NULL,
  `game_kills` int(10) UNSIGNED NOT NULL,
  `game_deaths` int(10) UNSIGNED NOT NULL,
  `game_score` int(10) UNSIGNED NOT NULL,
  `game_seconds` int(10) UNSIGNED NOT NULL,
  `game_ping` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `stats` (
  `id` int(11) NOT NULL,
  `user_id` int(11) UNSIGNED NOT NULL,
  `period_id` int(11) NOT NULL,
  `type_id` int(11) NOT NULL,
  `vs_rating_rank` int(11) NOT NULL DEFAULT '0',
  `vs_rating` int(11) NOT NULL DEFAULT '0',
  `score_rank` int(11) NOT NULL DEFAULT '0',
  `rank_points` int(11) NOT NULL DEFAULT '0',
  `points` int(11) NOT NULL DEFAULT '0',
  `kills` int(11) NOT NULL DEFAULT '0',
  `deaths` int(11) NOT NULL DEFAULT '0',
  `kill_streak` int(11) NOT NULL DEFAULT '0',
  `death_streak` int(11) NOT NULL DEFAULT '0',
  `stuns` int(11) NOT NULL DEFAULT '0',
  `stuns_received` int(11) NOT NULL DEFAULT '0',
  `head_shots` int(11) NOT NULL DEFAULT '0',
  `head_shots_received` int(11) NOT NULL DEFAULT '0',
  `kills_with_scorpion` int(11) NOT NULL DEFAULT '0',
  `kills_with_knife` int(11) NOT NULL DEFAULT '0',
  `cqc_attacks` int(11) NOT NULL DEFAULT '0',
  `cqc_attacks_received` int(11) NOT NULL DEFAULT '0',
  `teammate_kills` int(11) NOT NULL DEFAULT '0',
  `teammate_stuns` int(11) NOT NULL DEFAULT '0',
  `radio_uses` int(11) NOT NULL DEFAULT '0',
  `text_chat_uses` int(11) NOT NULL DEFAULT '0',
  `times_eaten` int(11) NOT NULL DEFAULT '0',
  `rolls` int(11) NOT NULL DEFAULT '0',
  `infrared_goggle_uses` int(11) NOT NULL DEFAULT '0',
  `team_wins` int(11) NOT NULL DEFAULT '0',
  `goals_reached_as_snake` int(11) NOT NULL DEFAULT '0',
  `snake_frags` int(11) NOT NULL DEFAULT '0',
  `kerotans_placed` int(11) NOT NULL DEFAULT '0',
  `kerotans_placed_for_win` int(11) NOT NULL DEFAULT '0',
  `gakos_rescued` int(11) NOT NULL DEFAULT '0',
  `rounds_played` int(11) NOT NULL DEFAULT '0',
  `rounds_played_without_dying` int(11) NOT NULL DEFAULT '0',
  `play_time` int(11) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `users` (
  `id` int(10) UNSIGNED NOT NULL,
  `username` varchar(15) NOT NULL,
  `displayname` varchar(15) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
  `password` varchar(32) NOT NULL,
  `session_id` varchar(32) NOT NULL DEFAULT '',
  `user_settings` varchar(428) NOT NULL DEFAULT '',
  `game_settings` varchar(428) NOT NULL DEFAULT '',
  `flags` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `emblem_text` varchar(15) NOT NULL DEFAULT '',
  `lobby_id` int(11) NOT NULL DEFAULT '0',
  `lastLogin` int(10) UNSIGNED NOT NULL DEFAULT '0',
  `alt_display` varchar(15) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


ALTER TABLE `connections`
  ADD PRIMARY KEY (`id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `games`
  ADD PRIMARY KEY (`id`),
  ADD KEY `lobby` (`lobby`),
  ADD KEY `host_id` (`host_id`);

ALTER TABLE `lists`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `type` (`type`,`owner_id`,`user_id`),
  ADD KEY `owner_id` (`owner_id`);

ALTER TABLE `lobbies`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `news`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `players`
  ADD UNIQUE KEY `id` (`id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `game` (`game`);

ALTER TABLE `stats`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `user_id_2` (`user_id`,`period_id`,`type_id`),
  ADD KEY `user_id` (`user_id`);

ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `displayname` (`displayname`);


ALTER TABLE `connections`
  MODIFY `id` int(11) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `games`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `lists`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `lobbies`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `news`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `players`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

ALTER TABLE `stats`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

ALTER TABLE `users`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;


ALTER TABLE `games`
  ADD CONSTRAINT `games_ibfk_1` FOREIGN KEY (`host_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `players`
  ADD CONSTRAINT `players_ibfk_1` FOREIGN KEY (`game`) REFERENCES `games` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `players_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `stats`
  ADD CONSTRAINT `stats_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
