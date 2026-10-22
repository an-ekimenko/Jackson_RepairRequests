package repair_requests.hibernate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.Session;
import org.hibernate.Transaction;

import repair_requests.model.RepairRequests_Entity;
import repair_requests.util.HibernateUtil;

/**
 * Helper DAO class for interaction with the database.
 */
public class RepairRequests_DAO {

    private static final Logger logger = LoggerFactory.getLogger(RepairRequests_DAO.class);

    public RepairRequests_DAO() {}
	
    /**
     * Save or update RepairRequests_Entity entities in the database using batch processing.
     *
     * @param listOfObj the list of entities to be synchronized with the database
     */
    public void saveOrUpdateBatch(List<RepairRequests_Entity> listOfObj) {
        if (listOfObj == null || listOfObj.isEmpty()) {
            return;
        }

        Transaction tr = null;
        
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tr = session.beginTransaction();

            int counter = 0;
            for (RepairRequests_Entity entity : listOfObj) {
                session.merge(entity);

                /**
                 * Periodically flush and clear the first-level session cache to prevent 
                 * memory overflow during large batch operations.
                 */
                if (++counter % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }

            tr.commit();
            
            System.out.printf("The batch of %d RepairRequests entities has been successfully saved/updated in MS SQL.\n", 
            		          listOfObj.size());
            logger.info("The batch of {} RepairRequests entities has been successfully saved/updated in MS SQL.", listOfObj.size());
        } catch (Exception ex) {
            if (tr != null && tr.isActive()) {
                tr.rollback();
            }
            logger.error("Error during batch saving of RepairRequests to the database: ", ex);
            throw ex;
        }
    }
}

