package au.edu.melbuni.boldapp;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

public class MainFragment extends Fragment {
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    // Inflate the layout for this fragment
	    View v = inflater.inflate(R.layout.main, container, false);
	    
        // Set button actions.
        //
        final ImageButton recordButton = (ImageButton) v.findViewById(R.id.recordButton);
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Fragment recordFragment = new RecordFragment();
                
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.content, recordFragment);
                fragmentTransaction.commit();
            }
        });
	    
        final ImageButton listenButton = (ImageButton) v.findViewById(R.id.listenButton);
        listenButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	
            }
        });
        
        final ImageButton respeakButton = (ImageButton) v.findViewById(R.id.respeakButton);
        respeakButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	
            }
        });
        
        final ImageButton translateButton = (ImageButton) v.findViewById(R.id.translateButton);
        translateButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	
            }
        });
	    
	    return v;
	}
}
