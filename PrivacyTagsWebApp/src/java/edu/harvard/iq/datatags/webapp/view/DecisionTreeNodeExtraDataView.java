package edu.harvard.iq.datatags.webapp.view;

import edu.harvard.iq.datatags.webapp.boundary.DecisionNodeExtraDataFacade;
import edu.harvard.iq.datatags.webapp.model.DecisionTreeNodeExtraData;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;

/**
 *
 * @author michael
 */
@ManagedBean( name="dtnExtraDataView" )
@RequestScoped
public class DecisionTreeNodeExtraDataView {
	@EJB
	private DecisionNodeExtraDataFacade dtnExtraDataFacade;
	
	final private DecisionTreeNodeExtraData extraData;
	
	/**
	 * Creates a new instance of DecisionTreeExtraDataView
	 */
	public DecisionTreeNodeExtraDataView() {
		extraData = new DecisionTreeNodeExtraData();
	}
	
	public String postNew() {
		dtnExtraDataFacade.create(extraData);
		return "newExtraDataAdded";
	}

	public DecisionTreeNodeExtraData getExtraData() {
		return extraData;
	}
	
	public int getExtraDataCount() {
		return dtnExtraDataFacade.count();
	}
}
