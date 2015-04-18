package info.blockchain.wallet;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Spannable;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.Account;
import info.blockchain.wallet.payload.ImportedAccount;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.payload.Tx;
import info.blockchain.wallet.util.DateUtil;
import info.blockchain.wallet.util.ExchangeRateFactory;
import info.blockchain.wallet.util.FloatingActionButton;
import info.blockchain.wallet.util.MonetaryUtil;
import info.blockchain.wallet.util.OSUtil;
import info.blockchain.wallet.util.PrefsUtil;
import info.blockchain.wallet.util.TypefaceUtil;

public class BalanceFragment extends Fragment {

	private Locale locale = null;

	//
	// main balance display
	//
	private TextView tvBalance1 = null;

	private double btc_balance = 0.0;
	private double fiat_balance = 0.0;
	private double btc_fx = 319.13;

	private Spannable span1 = null;
	private Spannable span2 = null;
	private final String strBTC = "BTC";
	private String strFiat = null;
	private boolean isBTC = true;

	//
	// accounts list
	//
	private List<Account> accounts = null;
	private Spinner accountSpinner = null;
	ArrayAdapter<String> accountsAdapter = null;
	private static int selectedAccount = 0;

	//
	// tx list
	//
	private HashMap<String,List<Tx>> txMap = null;
	private List<Tx> txs = new ArrayList<Tx>();
	private ListView txList = null;
	private TransactionAdapter txAdapter = null;

	public static final String ACTION_INTENT = "info.blockchain.wallet.BalanceFragment.REFRESH";

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        	if(ACTION_INTENT.equals(intent.getAction())) {

    	    	getActivity().runOnUiThread(new Runnable() {
   	    	     @Override
   	    	     public void run() {
                    displayBalance();
           	    	accountsAdapter.notifyDataSetChanged();
           	    	updateTx();
                   	txAdapter.notifyDataSetChanged();
   	    	     }
    	    	});

            }
        }
    };

	private SlidingUpPanelLayout mLayout;
	private LinearLayout bottomSel1 = null;
	private LinearLayout bottomSel2 = null;
	private LinearLayout mainContent;
	private LinearLayout mainContentShadow;

	public BalanceFragment() { ; }

	Communicator comm;
	ImageButton fab;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		View rootView = inflater.inflate(R.layout.fragment_balance, container, false);

		locale = Locale.getDefault();

		setHasOptionsMenu(true);
		((ActionBarActivity)getActivity()).getSupportActionBar().setDisplayShowTitleEnabled(false);
		accountSpinner = (Spinner)getActivity().findViewById(R.id.account_spinner);
		accountSpinner.setVisibility(View.VISIBLE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			fab = (ImageButton) rootView.findViewById(R.id.btActivateBottomSheet);
		else
			fab = (FloatingActionButton) rootView.findViewById(R.id.btActivateBottomSheet);

		fab.bringToFront();
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onAddClicked();
			}
		});

		tvBalance1 = (TextView)rootView.findViewById(R.id.balance1);
		tvBalance1.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());

		tvBalance1.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
            	isBTC = (isBTC) ? false : true;
            	displayBalance();
            	accountsAdapter.notifyDataSetChanged();
            	txAdapter.notifyDataSetChanged();
            	return false;
            }
        });

        accounts = PayloadFactory.getInstance().get().getHdWallet().getAccounts();
        if(accounts != null && accounts.size() > 0 && !(accounts.get(accounts.size() - 1) instanceof ImportedAccount) && (PayloadFactory.getInstance().get().getLegacyAddresses().size() > 0)) {
        	ImportedAccount iAccount = new ImportedAccount("Imported addresses", PayloadFactory.getInstance().get().getLegacyAddresses(), new ArrayList<String>(), MultiAddrFactory.getInstance().getLegacyBalance());
        	accounts.add(iAccount);
        }

		ArrayList<String> accountList = new ArrayList<String>();
        accountList.add(getActivity().getResources().getString(R.string.all_accounts));
		for(Account item : accounts)accountList.add(item.getLabel());

		accountsAdapter = new ArrayAdapter<String>(getActivity(),R.layout.spinner_title_bar, accountList.toArray(new String[0]));
		accountsAdapter.setDropDownViewResource(R.layout.spinner_title_bar_dropdown);
		accountSpinner.setAdapter(accountsAdapter);
        accountSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP && MainActivity.drawerIsOpen) {
                    return true;
                }
                else {
                    return false;
                }
            }
        });
		accountSpinner.post(new Runnable() {
			public void run() {
				accountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
					@Override
					public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
						int position = accountSpinner.getSelectedItemPosition();

						selectedAccount = position;
//		        		Log.i("account2Xpub", "position:" + selectedAccount);

						if(accounts == null || accounts.size() < 1) {
							return;
						}

                        if(selectedAccount == 0) {
                            txs = MultiAddrFactory.getInstance().getAllXpubTxs();
                        }
                        else {
                            String xpub = account2Xpub(selectedAccount - 1);
//		        		Log.i("account2Xpub", xpub);

                            if(xpub != null) {
                                if(MultiAddrFactory.getInstance().getXpubAmounts().containsKey(xpub)) {
                                    txs = txMap.get(xpub);
//        		        Log.i("account2Xpub", "M:" + txs.size());
                                }
                            }
                            else {
//    		       		 Log.i("account2Xpub", "xpub is null");
                                Account hda = accounts.get(selectedAccount - 1);
                                if(hda instanceof ImportedAccount) {
                                    txs = MultiAddrFactory.getInstance().getLegacyTxs();
//        		        Log.i("account2Xpub", "I:" + txs.size());
                                }
                            }

                        }

//						tvSwipe.setText(getAccountLabel() + "\n" + Character.toString((char)TypefaceUtil.awesome_angle_double_up) + "\n" + Character.toString((char)TypefaceUtil.awesome_angle_double_down));

                        displayBalance();

						txAdapter.notifyDataSetInvalidated();
					}
					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
						;
					}
				});
			}
		});
		accountSpinner.setSelection(selectedAccount);

        txList = (ListView)rootView.findViewById(R.id.txList);
        txAdapter = new TransactionAdapter();
        txList.setAdapter(txAdapter);
        /*
        txList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
            	Tx tx = txs.get(position);
            	String strTx = tx.getHash();
            	if(strTx != null) {
            		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/tx/" + strTx));
            		startActivity(browserIntent);            		
            	}
            }
        });
        */

        displayBalance();
        updateTx();

        // Name account now that wallet has been created
        if(PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, "").length() > 0) {
    		PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(0).setLabel(PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME, ""));
    		PrefsUtil.getInstance(getActivity()).removeValue(PrefsUtil.KEY_INITIAL_ACCOUNT_NAME);
    		PayloadFactory.getInstance(getActivity()).remoteSaveThread();
        	accountsAdapter.notifyDataSetChanged();
        }
        
		if(!OSUtil.getInstance(getActivity()).isServiceRunning(info.blockchain.wallet.service.WebSocketService.class)) {
			getActivity().startService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
		}
		else {
			getActivity().stopService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
			getActivity().startService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
		}

		mLayout = (SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout);
		mLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
		mLayout.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
			@Override
			public void onPanelSlide(View panel, float slideOffset) {
			}

			@Override
			public void onPanelExpanded(View panel) {

			}

			@Override
			public void onPanelCollapsed(View panel) {

			}

			@Override
			public void onPanelAnchored(View panel) {
			}

			@Override
			public void onPanelHidden(View panel) {
			}
		});
		bottomSel1 = ((LinearLayout)rootView.findViewById(R.id.bottom_sel1));
		bottomSel1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Fragment fragment = new SendFragment();
				Bundle args = new Bundle();
				args.putInt("selected_account", selectedAccount == 0 ? 0 : selectedAccount - 1);
				fragment.setArguments(args);
				FragmentManager fragmentManager = getFragmentManager();
				fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).addToBackStack(null).commit();

			}
		});
		bottomSel2 = ((LinearLayout)rootView.findViewById(R.id.bottom_sel2));
		bottomSel2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Fragment fragment = new ReceiveFragment();
				Bundle args = new Bundle();
				args.putInt("selected_account", selectedAccount == 0 ? 0 : selectedAccount - 1);
				fragment.setArguments(args);
				FragmentManager fragmentManager = getFragmentManager();
				fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).addToBackStack(null).commit();
			}
		});

		mainContent = (LinearLayout)rootView.findViewById(R.id.balance_main_content);
		mainContentShadow = (LinearLayout)rootView.findViewById(R.id.balance_main_content_shadow);
		mainContentShadow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(mLayout.getPanelState().equals(SlidingUpPanelLayout.PanelState.COLLAPSED)){
					onAddClicked();
				}
			}
		});

        return rootView;
	}

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if(isVisibleToUser) {
        	displayBalance();
        	accountsAdapter.notifyDataSetChanged();
        	txAdapter.notifyDataSetChanged();
        	updateTx();
        }
        else {
        	;
        }
    }

    @Override
    public void onResume() {
    	super.onResume();

		setNavigationDrawer();

        IntentFilter filter = new IntentFilter(ACTION_INTENT);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, filter);
        
		if(!OSUtil.getInstance(getActivity()).isServiceRunning(info.blockchain.wallet.service.WebSocketService.class)) {
			getActivity().startService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
		}
		else {
			getActivity().stopService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
			getActivity().startService(new Intent(getActivity(), info.blockchain.wallet.service.WebSocketService.class));
		}

    	displayBalance();
    	accountsAdapter.notifyDataSetChanged();
    	txAdapter.notifyDataSetChanged();
    	updateTx();
    }

    @Override
    public void onPause() {
    	super.onPause();
    	
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
    }

    private class TransactionAdapter extends BaseAdapter {
    	
		private LayoutInflater inflater = null;

		TextView tvResult;

	    TransactionAdapter() {
	        inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			if(txs == null) {
				txs = new ArrayList<Tx>();
			}
			return txs.size();
		}

		@Override
		public String getItem(int position) {
			if(txs == null) {
				txs = new ArrayList<Tx>();
			}
			return txs.get(position).toString();
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			View view = null;
	        
	        if (convertView == null) {
	            view = inflater.inflate(R.layout.txs_layout_simple, parent, false);
	        } else {
	            view = convertView;
	        }

	        if(txs != null) {
		        final Tx tx = txs.get(position);
		        double _btc_balance = tx.getAmount() / 1e8;
		    	double _fiat_balance = btc_fx * _btc_balance;
		    	
		    	tvResult = (TextView)view.findViewById(R.id.result);
				tvResult.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());
				tvResult.setTextColor(Color.WHITE);

		    	TextView tvTS = (TextView)view.findViewById(R.id.ts);
				tvTS.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());
//				tvTS.setTextColor(0xffadc0c9);
				tvTS.setText(DateUtil.getInstance(getActivity()).formatted(tx.getTS()));

		    	TextView tvDirection = (TextView)view.findViewById(R.id.direction);
				tvDirection.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());
//				tvDirection.setTextColor(0xff828181);
				tvDirection.setText(tx.getDirection());

				/*
	    		Spannable msg = Spannable.Factory.getInstance().newSpannable(tx.getDirection());
	    		msg.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    		msg.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), msg.length() - 15, msg.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				tvDirection.setText(msg);
				*/

//		    	TextView tvNote = (TextView)view.findViewById(R.id.note);
//				tvNote.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());
//				if(PayloadFactory.getInstance().get().getNotes().get(tx.getHash()) != null) {
//					tvNote.setVisibility(View.VISIBLE);
//					tvNote.setText(PayloadFactory.getInstance().get().getNotes().get(tx.getHash()));
//				}
//				else {
//					tvNote.setVisibility(View.INVISIBLE);
//				}

		        if(isBTC) {
//                    span1 = Spannable.Factory.getInstance().newSpannable(MonetaryUtil.getInstance().getBTCFormat().format(btc_balance) + " " + strBTC);
                    span1 = Spannable.Factory.getInstance().newSpannable(getDisplayAmount(tx.getAmount()) + " " + getDisplayUnits());
                    span1.setSpan(new RelativeSizeSpan(0.67f), span1.length() - getDisplayUnits().length(), span1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		        }
		        else	{
					span1 = Spannable.Factory.getInstance().newSpannable(MonetaryUtil.getInstance().getFiatFormat(strFiat).format(_fiat_balance) + " " + strFiat);
                    span1.setSpan(new RelativeSizeSpan(0.67f), span1.length() - 3, span1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		        }
				if(tx.isMove()) {
					tvResult.setBackgroundResource(tx.getConfirmations() < 3 ? R.drawable.rounded_view_lighter_blue_50 : R.drawable.rounded_view_lighter_blue);
					tvDirection.setTextColor(getActivity().getResources().getColor(tx.getConfirmations() < 3 ? R.color.blockchain_transfer_blue_50 : R.color.blockchain_transfer_blue));
				}
				else if(_btc_balance < 0.0) {
					tvResult.setBackgroundResource(tx.getConfirmations() < 3 ? R.drawable.rounded_view_red_50 : R.drawable.rounded_view_red);
					tvDirection.setTextColor(getActivity().getResources().getColor(tx.getConfirmations() < 3 ? R.color.blockchain_red_50 : R.color.blockchain_send_red));
				}
				else {
					tvResult.setBackgroundResource(tx.getConfirmations() < 3 ? R.drawable.rounded_view_green_50 : R.drawable.rounded_view_green);
					tvDirection.setTextColor(getActivity().getResources().getColor(tx.getConfirmations() < 3 ? R.color.blockchain_green_50 : R.color.blockchain_receive_green));
				}
				tvResult.setText(span1);
                tvResult.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {

						if (event.getAction() == MotionEvent.ACTION_UP) {
							isBTC = (isBTC) ? false : true;
							displayBalance();
							accountsAdapter.notifyDataSetChanged();
							txAdapter.notifyDataSetChanged();
						}
                        return true;
                    }
                });

				tvTS.setOnTouchListener(new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {

						if (event.getAction() == MotionEvent.ACTION_UP) {
							String strTx = tx.getHash();
							if (strTx != null) {
								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/tx/" + strTx));
								startActivity(browserIntent);
							}
						}
						return true;
					}
				});

				tvDirection.setOnTouchListener(new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {

						if (event.getAction() == MotionEvent.ACTION_UP) {
							String strTx = tx.getHash();
							if (strTx != null) {
								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/tx/" + strTx));
								startActivity(browserIntent);
							}
						}
						return true;
					}
				});

//                LinearLayout layoutTxInfo = (LinearLayout)view.findViewById(R.id.tx_info);
//                layoutTxInfo.setOnTouchListener(new OnTouchListener() {
//                    @Override
//                    public boolean onTouch(View v, MotionEvent event) {
//
//						if (event.getAction() == MotionEvent.ACTION_UP) {
//							String strTx = tx.getHash();
//							if (strTx != null) {
//								Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://blockchain.info/tx/" + strTx));
//								startActivity(browserIntent);
//							}
//						}
//                        return true;
//                    }
//                });

				/*
		    	TextView tvTags = (TextView)view.findViewById(R.id.tags);
				tvTags.setTypeface(TypefaceUtil.getInstance(getActivity()).getRobotoTypeface());
				int bullet = 0x25cf;	// other bullet == 0x2022
				SpannableStringBuilder strTags = new SpannableStringBuilder();
				Spannable span = null;
				int spanColor = 0;
				Map<Integer,String> tags = tx.getTags();
				List<Integer> keys = new ArrayList<Integer>();
				keys.addAll(tags.keySet());
				for(int i = 0; i < keys.size(); i++) {
					SpannableStringBuilder strTag = new SpannableStringBuilder();
					span = Spannable.Factory.getInstance().newSpannable(Character.toString((char)bullet) + " ");
					switch(keys.get(i)) {
					case 0:
						spanColor = Color.BLUE;
						break;
					case 1: 
						spanColor = Color.CYAN;
						break;
					case 2: 
						spanColor = Color.GREEN;
						break;
					case 3:
						spanColor = Color.RED;
						break;
					default:
						spanColor = Color.GRAY;
						break;
					}
					span.setSpan(new ForegroundColorSpan(spanColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					strTag.append(span);
					strTag.append(tags.get(keys.get(i)));
					if(i == (keys.size() - 1)) {
						strTag.append(" ");
					}
					strTags.append(strTag);
				}
				tvTags.setText(strTags);
				*/
	        }

	        return view;
		}

    }

	private void displayBalance() {
        strFiat = PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_SELECTED_FIAT, PrefsUtil.DEFAULT_CURRENCY);
        btc_fx = ExchangeRateFactory.getInstance(getActivity()).getLastPrice(strFiat);

        /*
        if(hda instanceof ImportedAccount) {
            btc_balance = ((double)MultiAddrFactory.getInstance().getLegacyBalance() / 1e8);
        }
        else if(selectedAccount == 0) {
            btc_balance = ((double)(MultiAddrFactory.getInstance().getXpubBalance()) / 1e8);
        }
        else {
            btc_balance = ((double)(MultiAddrFactory.getInstance().getXpubAmounts().get(account2Xpub(selectedAccount - 1))) / 1e8);
        }
        */
        Account hda = null;
        if(selectedAccount == 0) {
            btc_balance = ((double)MultiAddrFactory.getInstance().getXpubBalance() / 1e8);
        }
        else {
            hda = accounts.get(selectedAccount - 1);
            if(hda instanceof ImportedAccount) {
                btc_balance = ((double)MultiAddrFactory.getInstance().getLegacyBalance() / 1e8);
            }
            else {
                btc_balance = ((double)(MultiAddrFactory.getInstance().getXpubAmounts().get(account2Xpub(selectedAccount - 1))) / 1e8);
            }
        }

        fiat_balance = btc_fx * btc_balance;

        if(hda != null && hda instanceof ImportedAccount) {
            span1 = Spannable.Factory.getInstance().newSpannable(isBTC ? (getDisplayAmount(MultiAddrFactory.getInstance().getLegacyBalance()) + " " + getDisplayUnits()) : (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat));
            span2 = Spannable.Factory.getInstance().newSpannable(isBTC ? (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat) : (getDisplayAmount(MultiAddrFactory.getInstance().getLegacyBalance())));
        }
        else if(selectedAccount == 0) {
            span1 = Spannable.Factory.getInstance().newSpannable(isBTC ? (getDisplayAmount(MultiAddrFactory.getInstance().getXpubBalance()) + " " + getDisplayUnits()) : (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat));
            span2 = Spannable.Factory.getInstance().newSpannable(isBTC ? (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat) : (getDisplayAmount(MultiAddrFactory.getInstance().getXpubBalance()) + " " + getDisplayUnits()));
        }
        else {
            span1 = Spannable.Factory.getInstance().newSpannable(isBTC ? (getDisplayAmount(MultiAddrFactory.getInstance().getXpubAmounts().get(account2Xpub(selectedAccount - 1))) + " " + getDisplayUnits()) : (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat));
            span2 = Spannable.Factory.getInstance().newSpannable(isBTC ? (MonetaryUtil.getInstance().getFiatFormat(strFiat).format(fiat_balance) + " " + strFiat) : (getDisplayAmount(MultiAddrFactory.getInstance().getXpubAmounts().get(account2Xpub(selectedAccount - 1))) + " " + getDisplayUnits()));
        }
		span1.setSpan(new RelativeSizeSpan(0.67f), span1.length() - (isBTC ? getDisplayUnits().length() : 3), span1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		span2.setSpan(new RelativeSizeSpan(0.67f), span2.length() - (isBTC ? 3 : getDisplayUnits().length()), span2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		tvBalance1.setText(span1);
	}

    private String getDisplayAmount(long value) {

        String strAmount = null;

        int unit = PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC);
        switch(unit) {
            case MonetaryUtil.MICRO_BTC:
                strAmount = Double.toString((((double)(value * 1000000L)) / 1e8));
                break;
            case MonetaryUtil.MILLI_BTC:
                strAmount = Double.toString((((double)(value * 1000L)) / 1e8));
                break;
            default:
                strAmount = MonetaryUtil.getInstance().getBTCFormat().format(value / 1e8);
                break;
        }

        return strAmount;
    }

    private String getDisplayAmount(double value) {

        String strAmount = null;

        int unit = PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC);
        switch(unit) {
            case MonetaryUtil.MICRO_BTC:
                strAmount = Double.toString((value * 1000000.0) / 1e8);
                break;
            case MonetaryUtil.MILLI_BTC:
                strAmount = Double.toString((value * 1000.0) / 1e8);
                break;
            default:
                strAmount = MonetaryUtil.getInstance().getBTCFormat().format(value / 1e8);
                break;
        }

        return strAmount;
    }

    private String getDisplayUnits() {

        return (String)MonetaryUtil.getInstance().getBTCUnits()[PrefsUtil.getInstance(getActivity()).getValue(PrefsUtil.KEY_BTC_UNITS, MonetaryUtil.UNIT_BTC)];

    }

    private void updateTx() {

        txMap = MultiAddrFactory.getInstance().getXpubTxs();
		
    	if(accounts == null || accounts.size() < 1) {
    		return;
    	}

        if(selectedAccount == 0) {
            txs = MultiAddrFactory.getInstance().getAllXpubTxs();
        }
        else {
            String xpub = account2Xpub(selectedAccount - 1);

            if(xpub != null) {
                if(MultiAddrFactory.getInstance().getXpubAmounts().containsKey(xpub)) {
                    txs = txMap.get(xpub);
                }
            }
            else {
                Account hda = accounts.get(selectedAccount - 1);
                if(hda instanceof ImportedAccount) {
                    txs = MultiAddrFactory.getInstance().getLegacyTxs();
                }
            }

        }

	}

	private String account2Xpub(int accountIndex) {

		Account hda = accounts.get(accountIndex);
		String xpub = null;
	    if(hda instanceof ImportedAccount) {
	    	xpub = null;
	    }
	    else {
			xpub = HDPayloadBridge.getInstance(getActivity()).account2Xpub(accountIndex);
	    }
	    
	    return xpub;
	}

	private String getAccountLabel() {
		String ret = null;
        Account hda = accounts.get(selectedAccount);
        if(hda instanceof ImportedAccount) {
        	ret = "Imported addresses";
        }
        if(hda.getLabel() != null && hda.getLabel().length() > 0) {
        	ret = hda.getLabel();
        }
        else {
        	ret = "Account:" + selectedAccount;
        }
        
        return ret;
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		menu.findItem(R.id.action_merchant_directory).setVisible(true);
		menu.findItem(R.id.action_qr).setVisible(true);
		menu.findItem(R.id.action_send).setVisible(false);
		menu.findItem(R.id.action_share_receive).setVisible(false);
	}

	private void onAddClicked(){

		if (mLayout != null) {
			if (mLayout.getPanelState() != SlidingUpPanelLayout.PanelState.HIDDEN) {
				mLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
				mainContentShadow.setVisibility(View.GONE);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)fab.setElevation(8);
			} else {
				mLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
				mainContentShadow.bringToFront();
				mainContentShadow.setVisibility(View.VISIBLE);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)fab.setElevation(0);
			}
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		comm=(Communicator)activity;
	}

	interface Communicator{

		public void setNavigationDrawer();
	}

	private void setNavigationDrawer(){
		comm.setNavigationDrawer();
	}
}
