//
// $Id: UserRepository.java,v 1.14 2001/08/11 22:43:28 mdb Exp $
//
// samskivert library - useful routines for java programs
// Copyright (C) 2001 Michael Bayne
// 
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.samskivert.servlet.user;

import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;

import org.apache.regexp.*;

import com.samskivert.Log;
import com.samskivert.jdbc.JDBCUtil;
import com.samskivert.jdbc.MySQLRepository;
import com.samskivert.jdbc.jora.*;
import com.samskivert.util.IntMap;

public class UserRepository extends MySQLRepository
{
    /**
     * Creates the repository and opens the user database. A properties
     * object should be supplied with the following fields:
     *
     * <pre>
     * driver=[jdbc driver class]
     * url=[jdbc driver url]
     * username=[jdbc username]
     * password=[jdbc password]
     * </pre>
     *
     * @param props a properties object containing the configuration
     * parameters for the repository.
     */
    public UserRepository (Properties props)
	throws SQLException
    {
	super(props);
    }

    protected void createTables ()
	throws SQLException
    {
	// create our table object
	_utable = new Table(User.class.getName(), "users", _session,
			    "userid");
    }

    /**
     * Requests that a new user be created in the repository.
     *
     * @param username The username of the new user to create. Usernames
     * must consist only of characters that match the following regular
     * expression: <code>[_A-Za-z0-9]+</code> and be longer than two
     * characters.
     * @param password The (unencrypted) password for the new user.
     * @param realname The user's real name.
     * @param email The user's email address.
     *
     * @return The userid of the newly created user.
     */
    public int createUser (String username, String password,
			   String realname, String email)
	throws InvalidUsernameException, UserExistsException, SQLException
    {
	// check minimum length
	if (username.length() < MINIMUM_USERNAME_LENGTH) {
	    throw new InvalidUsernameException("error.username_too_short");
	}

	// check that it's only valid characters
	if (!_userre.match(username)) {
	    throw new InvalidUsernameException("error.invalid_username");
	}

	// create a new user object and stick it into the database
	final User user = new User();
	user.username = username;
	user.password = UserUtil.encryptPassword(username, password);
	user.realname = realname;
	user.email = email;
	user.created = new Date(System.currentTimeMillis());

	try {
	    execute(new Operation () {
		public Object invoke () throws SQLException
		{
		    _utable.insert(user);
		    // update the userid now that it's known
		    user.userid = lastInsertedId();
                    // nothing to return
                    return null;
		}
	    });

	} catch (SQLException sqe) {
	    if (isDuplicateRowException(sqe)) {
		throw new UserExistsException("error.user_exists");
	    } else {
		throw sqe;
	    }
	}

	return user.userid;
    }

    /**
     * Looks up a user by username.
     *
     * @return the user with the specified user id or null if no user with
     * that id exists.
     */
    public User loadUser (final String username)
	throws SQLException
    {
        return (User)execute(new Operation () {
            public Object invoke () throws SQLException
            {
                // look up the user
                String query = "where username = '" + username + "'";
                Cursor ec = _utable.select(query);

                // fetch the user from the cursor
                User user = (User)ec.next();
                if (user != null) {
                    // call next() again to cause the cursor to close itself
                    ec.next();
                }

                return user;
            }
        });
    }

    /**
     * Looks up a user by userid.
     *
     * @return the user with the specified user id or null if no user with
     * that id exists.
     */
    public User loadUser (final int userid)
	throws SQLException
    {
        return (User)execute(new Operation () {
            public Object invoke () throws SQLException
            {
                // look up the user
                Cursor ec = _utable.select("where userid = " + userid);

                // fetch the user from the cursor
                User user = (User)ec.next();
                if (user != null) {
                    // call next() again to cause the cursor to close itself
                    ec.next();
                }

                return user;
            }
        });
    }

    /**
     * Looks up a user by a session identifier.
     *
     * @return the user associated with the specified session or null of
     * no session exists with the supplied identifier.
     */
    public User loadUserBySession (final String sessionKey)
	throws SQLException
    {
        return (User)execute(new Operation () {
            public Object invoke () throws SQLException
            {
                String query = "where authcode = '" + sessionKey +
                    "' AND sessions.userid = users.userid";
                // look up the user
                Cursor ec = _utable.select("sessions", query);

                // fetch the user from the cursor
                User user = (User)ec.next();
                if (user != null) {
                    // call next() again to cause the cursor to close itself
                    ec.next();
                }

                return user;
            }
        });
    }

    /**
     * Updates a user that was previously fetched from the repository.
     */
    public void updateUser (final User user)
	throws SQLException
    {
	execute(new Operation () {
	    public Object invoke () throws SQLException
	    {
		_utable.update(user);
                // nothing to return
                return null;
	    }
	});
    }

    /**
     * Removes the user from the repository.
     */
    public void deleteUser (final User user)
	throws SQLException
    {
	execute(new Operation () {
	    public Object invoke () throws SQLException
	    {
		_utable.delete(user);
                // nothing to return
                return null;
	    }
	});
    }

    /**
     * Creates a new session for the specified user and returns the
     * randomly generated session identifier for that session. Temporary
     * sessions are set to expire in two days which prevents someone from
     * being screwed if they log in at 11:59pm, but also prevents them
     * from leaving their browser authenticated for too long. Persistent
     * sessions expire after one month.
     */
    public String createNewSession (final User user, boolean persist)
	throws SQLException
    {
	// generate a random session identifier
	final String authcode = UserUtil.genAuthCode(user);

	// figure out when to expire the session
	Calendar cal = Calendar.getInstance();
	cal.add(Calendar.DATE, persist ? 30 : 2);
	final Date expires = new Date(cal.getTime().getTime());

	// insert the session into the database
	execute(new Operation () {
	    public Object invoke () throws SQLException
	    {
		_session.execute("insert into sessions " +
				 "(authcode, userid, expires) values('" +
				 authcode + "', " + user.userid + ", '" +
				 expires + "')");
                // nothing to return
                return null;
	    }
	});

	// and let the user know what the session identifier is
	return authcode;
    }

    /**
     * Prunes any expired sessions from the sessions table.
     */
    public void pruneSessions ()
	throws SQLException
    {
	execute(new Operation () {
	    public Object invoke () throws SQLException
	    {
		_session.execute("delete from sessions where " +
				 "expires <= CURRENT_DATE()");
                // nothing to return
                return null;
	    }
	});
    }

    /**
     * Loads the usernames of the users identified by the supplied user
     * ids and returns them in an array. If any users do not exist, their
     * slot in the array will contain a null.
     */
    public String[] loadUserNames (int[] userids)
	throws SQLException
    {
	return loadNames(userids, "username");
    }

    /**
     * Loads the real names of the users identified by the supplied user
     * ids and returns them in an array. If any users do not exist, their
     * slot in the array will contain a null.
     */
    public String[] loadRealNames (int[] userids)
	throws SQLException
    {
	return loadNames(userids, "realname");
    }

    protected String[] loadNames (int[] userids, final String column)
	throws SQLException
    {
        // if userids is zero length, we've got no work to do
        if (userids.length == 0) {
            return new String[0];
        }

	// build up the string we need for the query
	final StringBuffer ids = new StringBuffer();
	for (int i = 0; i < userids.length; i++) {
	    if (ids.length() > 0) {
		ids.append(", ");
	    }
	    ids.append(userids[i]);
	}

	final IntMap map = new IntMap();

	// do the query
        execute(new Operation () {
            public Object invoke () throws SQLException
            {
                Statement stmt = _session.connection.createStatement();
                try {
                    String query = "select userid, " + column +
                        " from users " +
                        "where userid in (" + ids + ")";
                    ResultSet rs = stmt.executeQuery(query);
                    while (rs.next()) {
                        int userid = rs.getInt(1);
                        String name = rs.getString(2);
                        map.put(userid, name);
                    }

                    // nothing to return
                    return null;

                } finally {
                    JDBCUtil.close(stmt);
                }
            }
        });

	// finally construct our result
	String[] result = new String[userids.length];
	for (int i = 0; i < userids.length; i++) {
	    result[i] = (String)map.get(userids[i]);
	}

	return result;
    }

    /**
     * Returns an array with the real names of every user in the system.
     * This is for Paul who whined about not knowing who was using Who,
     * Where, When because he didn't feel like emailing anyone that wasn't
     * already using it to link up.
     */
    public String[] loadAllRealNames ()
	throws SQLException
    {
        final ArrayList names = new ArrayList();

	// do the query
        execute(new Operation () {
            public Object invoke () throws SQLException
            {
                Statement stmt = _session.connection.createStatement();
                try {
                    String query = "select realname from users";
                    ResultSet rs = stmt.executeQuery(query);
                    while (rs.next()) {
                        names.add(rs.getString(1));
                    }

                    // nothing to return
                    return null;

                } finally {
                    JDBCUtil.close(stmt);
                }
            }
        });

	// finally construct our result
	String[] result = new String[names.size()];
        names.toArray(result);
	return result;
    }

    public static void main (String[] args)
    {
	Properties props = new Properties();
	props.put("driver", "org.gjt.mm.mysql.Driver");
	props.put("url", "jdbc:mysql://localhost:3306/samskivert");
	props.put("username", "www");
	props.put("password", "Il0ve2PL@Y");

	try {
	    UserRepository rep = new UserRepository(props);

	    System.out.println(rep.loadUser("mdb"));
	    System.out.println(rep.loadUserBySession("auth"));

	    rep.createUser("samskivert", "foobar", "Michael Bayne",
			   "mdb@samskivert.com");
	    rep.createUser("mdb", "foobar", "Michael Bayne",
			   "mdb@samskivert.com");

	    rep.shutdown();

	} catch (Throwable t) {
	    t.printStackTrace(System.err);
	}
    }

    protected Table _utable;

    /** Used to check usernames for invalid characteres. */
    protected static RE _userre;
    static {
	try {
	    _userre = new RE("^[_A-Za-z0-9]+$");
	} catch (RESyntaxException rese) {
	    Log.warning("Unable to initialize user regexp?! " + rese);
	}
    }

    /** The minimum allowable length of a username. */
    protected static final int MINIMUM_USERNAME_LENGTH = 3;
}
