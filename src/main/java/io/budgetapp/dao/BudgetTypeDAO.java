package io.budgetapp.dao;

import io.budgetapp.checker.ConsistencyChecker;
import io.budgetapp.database.MySqlConnector;
import io.budgetapp.database.PostgresConnector;
import io.budgetapp.model.BudgetType;
import io.dropwizard.hibernate.AbstractDAO;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;

import org.hibernate.SessionFactory;

/**
 *
 */
public class BudgetTypeDAO extends AbstractDAO<BudgetType> {

    public BudgetTypeDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public BudgetType addBudgetType() {
        BudgetType budgetType = new BudgetType();
        
        //***BEGIN Shadow write to mysql***
        if(MySqlConnector.getInstance().isUseMySql()) {
        	try {
    			// Inserting data
    			String query = " INSERT INTO budget_types (created_at) VALUES (?)";
    			PreparedStatement preparedStmt = MySqlConnector.getInstance().getMySqlConnection().prepareStatement(query);
    			preparedStmt.setTimestamp(1, new Timestamp(new java.util.Date().getTime()));

    			try {
    				preparedStmt.execute();
    				//Check consistency
    				ConsistencyChecker checker = new ConsistencyChecker(PostgresConnector.getInstance().getPostgresConnection(), MySqlConnector.getInstance().getMySqlConnection());
    				checker.checkBudgetTypes();
    				if(checker.getNumInconsistencies() > 100) {
    					budgetType = persist(budgetType);
    				}
    			}
    			catch (SQLIntegrityConstraintViolationException  e) {
    				e.printStackTrace();
    			}

    		} catch (SQLException e) {
    			e.printStackTrace();
    		}
            //***END Shadow write to mysql ***
        }
        
        
        return budgetType;
    }
}
