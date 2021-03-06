package io.budgetapp.dao;

import io.budgetapp.checker.ConsistencyChecker;
import io.budgetapp.database.MySqlConnector;
import io.budgetapp.database.PostgresConnector;
import io.budgetapp.model.Recurring;
import io.budgetapp.model.RecurringType;
import io.budgetapp.model.User;
import io.budgetapp.model.Recurring;
import io.budgetapp.model.RecurringType;
import io.budgetapp.model.User;
import io.budgetapp.util.Util;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;

/**
 *
 */
public class RecurringDAO extends AbstractDAO<Recurring> {

    public RecurringDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Recurring addRecurring(Recurring recurring) {
    	
    	Recurring newRecurring = new Recurring();
    	
    	//***BEGIN Shadow write to mysql***
    	if(MySqlConnector.getInstance().isUseMySql()) {
    		try {

    			//Disable foreign key checks
    			Statement disableFKChecks = MySqlConnector.getInstance().getMySqlConnection().createStatement();
    			disableFKChecks.executeQuery("SET FOREIGN_KEY_CHECKS=0");

    			// Inserting data 
    			String query = " INSERT INTO recurrings (amount, type, last_run_at, created_at, budget_type_id)"
    					+ " VALUES (?, ?, ?, ?, ?)";
    			PreparedStatement preparedStmt = MySqlConnector.getInstance().getMySqlConnection().prepareStatement(query);
    			preparedStmt.setDouble(1, recurring.getAmount());
    			preparedStmt.setString(2, recurring.getRecurringTypeDisplay());
    			preparedStmt.setTimestamp(3, (Timestamp) recurring.getLastRunAt());
    			preparedStmt.setTimestamp(4, new Timestamp(new java.util.Date().getTime()));
    			preparedStmt.setLong(5, recurring.getBudgetType().getId());

    			try {
    				preparedStmt.execute();
    				//Check consistency
    				ConsistencyChecker checker = new ConsistencyChecker(PostgresConnector.getInstance().getPostgresConnection(), MySqlConnector.getInstance().getMySqlConnection());
    				checker.checkRecurrings();
    				if(checker.getNumInconsistencies() > 100) {
    					newRecurring = persist(recurring);
    				}
    			}
    			catch (SQLIntegrityConstraintViolationException  e) {
    				e.printStackTrace();
    			}

    			//Enable foreign key checks
    			Statement enableFKChecks = MySqlConnector.getInstance().getMySqlConnection().createStatement();
    			enableFKChecks.executeQuery("SET FOREIGN_KEY_CHECKS=1");

    		} catch (SQLException e) {
    			e.printStackTrace();
    		}
        	//***END Shadow write to mysql***
    	}
    	
    	
        return persist(recurring);
    }

    public List<Recurring> findRecurrings(User user) {
        return currentSession().createQuery("SELECT r FROM Recurring r JOIN r.budgetType budgetType WHERE budgetType IN (SELECT budget.budgetType FROM Budget budget WHERE budget.user = :user)")
                .setParameter("user", user)
                .list();
    }

    public void delete(Recurring recurring) {
        currentSession().delete(recurring);
    }

    public Recurring find(User user, long recurringId) {
        return (Recurring)currentSession().createQuery("SELECT r FROM Recurring r JOIN r.budgetType budgetType WHERE budgetType IN (FROM Budget budget WHERE budget.user = :user and r.id = :id)")
                .setParameter("user", user)
                .setParameter("id", recurringId).uniqueResult();
    }

    public List<Recurring> findByBudgetTypeId(long budgetTypeId) {
        return currentSession().createQuery("SELECT r FROM Recurring r WHERE r.budgetType.id = :budgetTypeId")
                .setParameter("budgetTypeId", budgetTypeId)
                .list();
    }

    public List<Recurring> findActiveRecurrings() {
        LocalDate now = LocalDate.now();

        return currentSession()
                .createQuery("SELECT r FROM Recurring r WHERE " +
                        "(r.recurringType = :daily AND DAY(r.lastRunAt) = :yesterday) OR " +
                        // last week with same day of week
                        "(r.recurringType = :weekly AND WEEK(r.lastRunAt) = :lastWeek AND DAYOFWEEK(r.lastRunAt) = DAYOFWEEK(CURRENT_TIMESTAMP)) OR " +
                        // last month with same day of month
                        "(r.recurringType = :monthly AND MONTH(r.lastRunAt) = :lastMonth AND DAY(r.lastRunAt) = DAY(CURRENT_TIMESTAMP)) OR " +
                        // last year with same month
                        "(r.recurringType = :yearly AND YEAR(r.lastRunAt) = :lastYear AND MONTH(r.lastRunAt) = MONTH(CURRENT_TIMESTAMP)) OR " +
                        "r.lastRunAt IS NULL")
                .setParameter("daily", RecurringType.DAILY)
                .setParameter("yesterday", Util.yesterday(now))
                .setParameter("weekly", RecurringType.WEEKLY)
                .setParameter("lastWeek", Util.lastWeek(now))
                .setParameter("monthly", RecurringType.MONTHLY)
                .setParameter("lastMonth", Util.lastMonth(now))
                .setParameter("yearly", RecurringType.YEARLY)
                .setParameter("lastYear", now.minusYears(1).getYear())
                .list();
    }

    public void update(Recurring recurring) {
        persist(recurring);
    }
}
