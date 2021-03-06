package net.bioclipse.opentox.qsar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.IMolecule;
import net.bioclipse.opentox.Activator;
import net.bioclipse.opentox.business.IOpentoxManager;
import net.bioclipse.qsar.DescriptorType;
import net.bioclipse.qsar.business.IQsarManager;
import net.bioclipse.qsar.descriptor.DescriptorResult;
import net.bioclipse.qsar.descriptor.IDescriptorCalculator;
import net.bioclipse.qsar.descriptor.IDescriptorResult;
import net.bioclipse.qsar.descriptor.model.DescriptorImpl;

/**
 * This calculator is initialized with a service.
 * 
 * @author ola
 *
 */
public class OpenToxDescriptorCalculator implements IDescriptorCalculator {

    private static final Logger logger = Logger.getLogger(
    		OpenToxDescriptorCalculator.class);

	//Map from BODO to OpenTox Algo ID, used to invoke descriptor
	private Map<String, String> ontologyMap;
	private String service;
	private String providerID;

	public OpenToxDescriptorCalculator(String providerID, String service) {
		super();
		this.providerID=providerID;
		this.service=service;
	}
	
	public OpenToxDescriptorCalculator() {
	}

	@Override
	public Map<? extends IMolecule, List<IDescriptorResult>> calculateDescriptor(
			Map<IMolecule, List<DescriptorType>> moldesc,
			IProgressMonitor monitor) throws BioclipseException {

		//The list to return
		Map<IMolecule, List<IDescriptorResult>> allResults=
			new HashMap<IMolecule, List<IDescriptorResult>>();

		//The workload for this provider is mols x their descs
		int workload=0;
		for (IMolecule mol : moldesc.keySet()){
			workload=workload+moldesc.get( mol ).size();
		}

		monitor.beginTask( "Calculating OenTox descriptors" , workload );

		monitor.subTask("Verifying server...");
		//Verify server before processing molecules
		try {
			verifyServer();
		} catch ( Exception e ) {
			throw new BioclipseException("Could not contact OpenTox service: " 
					+ ontologyMap);
		}

        IQsarManager qsar = net.bioclipse.qsar.init.Activator
        .getDefault().getJavaQsarManager();
        IOpentoxManager opentox=Activator.getDefault().getJavaOpentoxManager();

		//We get descriptors as BODO entries, we need to map to OpenTox IDs
		
		for (IMolecule mol : moldesc.keySet()){

			//Hold results for this mol
            List<IDescriptorResult> molResults=
                new ArrayList<IDescriptorResult>();

			for (DescriptorType desc : moldesc.get( mol )){
				//Calculate desc for mol
				
                if (monitor.isCanceled())
                    throw new OperationCanceledException();

        		DescriptorImpl dimpl = qsar.getDescriptorImpl( 
        				desc.getOntologyid(), providerID );

        		//descriptor class
        		String descOTid=dimpl.getId();

        		monitor.subTask("Calculating OT descriptor: " + dimpl.getName());
        		monitor.worked(1);

        		//Invoke calculation
        		logger.debug("Trying service: " + service + " OTdescriptor: " + descOTid);
        		List<String> OTres = null;
    			//retry 5 times, looks like a server issue
    			for (int i=0; i<6; i++){
    				if (i>0)
    					logger.debug("  - Opentox descr calculation retry number " + i);
        			
            		try{
            			OTres = opentox.calculateDescriptor(service, descOTid, mol);
            		}catch(Exception e){
    					logger.error("  == Opentox calculation failed");
            		}
            		
            		//End if we have results
            		if (OTres!=null) break;
    				
    			}


        		//Handle results
    			IDescriptorResult res = parseOTResults(OTres, desc);
    			molResults.add (res);

			} //get next descriptor
			
			allResults.put(mol, molResults);
			
		} //get next mol
		
		monitor.done();

		return allResults;
	}

	
	private IDescriptorResult parseOTResults(List<String> OTres,
			DescriptorType desc) {
		
		IDescriptorResult res = new DescriptorResult();
		
		//If error
		if (OTres==null){
			res.setDescriptor(desc);
			res.setErrorMessage("OpenTox descriptor " + desc.getId() 
					+ " returned NULL");
		}else{
			//TODO: all is well, put results in QSAR model
			Float[] floats = new Float[OTres.size()];
			String[] labels= new String[OTres.size()];
			String baseLabel=desc.getOntologyid().substring(desc.getOntologyid().lastIndexOf("#")+1);
			for (int i=0; i<OTres.size();i++) {
				floats[i] = Float.parseFloat(OTres.get(i));
				if (OTres.size()>1)
					labels[i] = baseLabel + "-" + (i+1);
				else
					labels[i] = baseLabel;
			}
			res.setValues(floats);
			res.setLabels(labels);
			res.setDescriptor(desc);
		}

		return res;
	}


	
	
	private void verifyServer() {
		// TODO Auto-generated method stub

	}

}
