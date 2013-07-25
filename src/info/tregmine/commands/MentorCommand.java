package info.tregmine.commands;

import java.util.Queue;
import java.sql.Connection;
import java.sql.SQLException;

import static org.bukkit.ChatColor.*;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;

import info.tregmine.Tregmine;
import info.tregmine.api.TregminePlayer;
import info.tregmine.database.ConnectionPool;
import info.tregmine.database.DBPlayerDAO;

public class MentorCommand extends AbstractCommand
{
    public static class UpgradeTask implements Runnable
    {
        private TregminePlayer mentor;
        private TregminePlayer student;

        public UpgradeTask(TregminePlayer mentor, TregminePlayer student)
        {
            this.student = student;
            this.mentor = mentor;
        }

        @Override
        public void run()
        {
            if (!mentor.isValid()) {
                return;
            }
            if (!student.isValid()) {
                return;
            }

            mentor.sendMessage(BLUE + "Mentoring of " + student.getChatName() +
                    BLUE + " has now finished!");
            mentor.giveExp(100);

            student.sendMessage(BLUE + "Congratulations! You have now achieved " +
                    "settler status. We hope you'll enjoy your stay on Tregmine!");

            Connection conn = null;
            try {
                conn = ConnectionPool.getConnection();

                student.setTrusted(true);
                student.setNameColor("trial");

                DBPlayerDAO playerDAO = new DBPlayerDAO(conn);
                playerDAO.updatePlayerInfo(student);
                playerDAO.updatePlayerPermissions(student);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                    }
                }
            }
        }
    }

    public MentorCommand(Tregmine tregmine)
    {
        super(tregmine, "mentor");
    }

    @Override
    public boolean handlePlayer(TregminePlayer player, String[] args)
    {
        // residents, donator, not warned players
        if (!player.isResident()) {
            player.sendMessage(BLUE + "Only residents and above can mentor " +
                    "new players.");
            return true;
        }

        String action = "queue";
        if (args.length > 0) {
            action = args[0];
        }

        if ("queue".equalsIgnoreCase(action)) {
            Queue<TregminePlayer> students = tregmine.getStudentQueue();
            if (students.size() > 0) {
                TregminePlayer student = students.poll();
                startMentoring(tregmine, student, player);
                return true;
            }

            Queue<TregminePlayer> mentors = tregmine.getMentorQueue();
            mentors.offer(player);

            player.sendMessage(BLUE + "You are now part of the mentor queue. " +
                    "You are number " + mentors.size() + ". Type /mentor cancel " +
                    "to opt out.");
        }
        else if ("cancel".equalsIgnoreCase(action)) {
            Queue<TregminePlayer> mentors = tregmine.getMentorQueue();
            if (!mentors.contains(player)) {
                player.sendMessage(BLUE + "You are not part of the mentor queue. " +
                        "If you have already been assigned a student, you cannot " +
                        "about the mentoring.");
                return true;
            }
            mentors.remove(player);
        }
        else {
            return false;
        }

        return true;
    }

    public static void startMentoring(Tregmine tregmine,
                                      TregminePlayer student,
                                      TregminePlayer mentor)
    {
        if (student.getMentorId() != mentor.getId()) {
            Connection conn = null;
            try {
                conn = ConnectionPool.getConnection();

                student.setMentorId(mentor.getId());

                DBPlayerDAO playerDAO = new DBPlayerDAO(conn);
                playerDAO.updatePlayerInfo(student);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                    }
                }
            }

            Tregmine.LOGGER.info("[MENTOR] " + mentor.getChatName() + " is " +
                    "mentoring " + student.getChatName());

            // Instructiosn for students
            student.sendMessage(mentor.getChatName() + BLUE +
                    " has been assigned as your mentor!");
            student.sendMessage(BLUE + "He or she will show you " +
                    "around, answer any questions, and help you find a place " +
                    "to build.");

            // Instructions for mentor
            mentor.sendMessage(BLUE + "You have been assigned to " +
                    "mentor " + student.getChatName() + BLUE + ".");
            mentor.sendMessage(BLUE + "Please do this: ");
            mentor.sendMessage(BLUE + "1. Explain basic rules");
            mentor.sendMessage(BLUE + "2. Demonstrate basic commands");
            mentor.sendMessage(BLUE + "3. Show him or her around");
            mentor.sendMessage(BLUE + "4. Help him or her to find a lot " +
                    "and start building. If you own a zone, you may sell " +
                    "a lot, but keep in mind that it might be a good idea " +
                    "to let other players make offers too.");
            mentor.sendMessage(BLUE + "Scamming new players will not be  "+
                    "tolerated.");
            mentor.sendMessage(BLUE + "For the next fifteen minutes, your student " +
                    "will only be able to build in lots he or she owns. After " +
                    "that time has passed, the student will be automatically upgraded " +
                    "to settler status, and will be able to build everywhere.");
            mentor.sendMessage(BLUE + "Please start by teleporting to " +
                    student.getChatName() + "!");
        } else {
            student.sendMessage(BLUE + "Mentoring resuming.");
            mentor.sendMessage(BLUE + "Mentoring resuming.");
        }

        int timeRemaining = Math.max(60*15 - student.getTimeOnline(), 0);

        UpgradeTask task = new UpgradeTask(mentor, student);
        if (timeRemaining > 0) {
            Server server = tregmine.getServer();
            BukkitScheduler scheduler = server.getScheduler();
            scheduler.scheduleSyncDelayedTask(tregmine, task, 20 * timeRemaining);
        } else {
            task.run();
        }
    }
}