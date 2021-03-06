/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016 Ivan Vaklinov <ivan@vaklinov.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 **********************************************************************************/
package com.vaklinov.zcashui;


import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EtchedBorder;

import com.vaklinov.zcashui.ZCashClientCaller.WalletCallException;


/**
 * Addresses...
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class AddressesPanel
	extends JPanel
{
	private static final String T_ADDRESSES_FILE = "CreatedTransparentAddresses.txt";
	
	private ZCashClientCaller clientCaller;
	private StatusUpdateErrorReporter errorReporter;

	private JTable addressBalanceTable   = null;
	private JScrollPane addressBalanceTablePane  = null;
	
	String[][] lastAddressBalanceData = null;

	public AddressesPanel(ZCashClientCaller clientCaller, StatusUpdateErrorReporter errorReporter)
		throws IOException, InterruptedException, WalletCallException
	{
		this.clientCaller = clientCaller;
		this.errorReporter = errorReporter;

		// Build content
		JPanel addressesPanel = this;
		addressesPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		addressesPanel.setLayout(new BorderLayout(0, 0));
	
		// Build panel of buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
		buttonPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		
		JButton newTAddressButton = new JButton("New T (Transparent) address");
		buttonPanel.add(newTAddressButton);
		JButton newZAddressButton = new JButton("New Z (Private) address");
		buttonPanel.add(newZAddressButton);
		buttonPanel.add(new JLabel("           "));
		JButton refreshButton = new JButton("Refresh");
		buttonPanel.add(refreshButton);
		
		addressesPanel.add(buttonPanel, BorderLayout.SOUTH);

		// Table of transactions
		lastAddressBalanceData = getAddressBalanceDataFromWallet();
		addressesPanel.add(addressBalanceTablePane = new JScrollPane(
				               addressBalanceTable = this.createAddressBalanceTable(lastAddressBalanceData)),
				           BorderLayout.CENTER);
		
		
		JPanel warningPanel = new JPanel();
		warningPanel.setLayout(new BorderLayout(3, 3));
		warningPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		JLabel warningL = new JLabel(
				"<html><span style=\"font-size:8px;\">" +
				"* If the balance of an address is flagged as not confirmed, the address is currently taking " +
				"part in a transaction. The shown balance then is the expected value it will have when " +
				"the transaction is confirmed. " +
				"The average confirmation time is 2.5 min." +
			    "</span>");
		warningPanel.add(warningL, BorderLayout.NORTH);
		addressesPanel.add(warningPanel, BorderLayout.NORTH);
		
		
		// Button actions
		refreshButton.addActionListener(new ActionListener() 
		{	
			public void actionPerformed(ActionEvent e) 
			{
				try
				{
					// TODO: Hourglass cursor + dummy progress bar ...
					AddressesPanel.this.updateWalletAddressBalanceTable();
				} catch (Exception ex)
				{
					ex.printStackTrace();
					AddressesPanel.this.errorReporter.reportError(ex, false);
				}
			}
		});
		
		newTAddressButton.addActionListener(new ActionListener() 
		{	
			public void actionPerformed(ActionEvent e) 
			{
				createNewAddress(false);
			}
		});
		
		newZAddressButton.addActionListener(new ActionListener() 
		{	
			public void actionPerformed(ActionEvent e) 
			{
				createNewAddress(true);
			}
		});
		
		// TODO: validate stored t addresses on startup
	}

	
	private void createNewAddress(boolean isZAddress)
	{
		try
		{
			// Check for encrypted wallet
			final boolean bEncryptedWallet = this.clientCaller.isWalletEncrypted();
			if (bEncryptedWallet && isZAddress)
			{
				PasswordDialog pd = new PasswordDialog((JFrame)(this.getRootPane().getParent()));
				pd.setVisible(true);
				
				if (!pd.isOKPressed())
				{
					return;
				}
				
				this.clientCaller.unlockWallet(pd.getPassword());
			}

			String address = this.clientCaller.createNewAddress(isZAddress);
			
			// Lock the wallet again 
			if (bEncryptedWallet && isZAddress)
			{
				this.clientCaller.lockWallet();
			}
						
			JOptionPane.showMessageDialog(
				this.getRootPane().getParent(), 
				"A new " + (isZAddress ? "Z (Private)" : "T (Transparent)") 
				+ " address has been created cuccessfully:\n" + address, 
				"Address created", JOptionPane.INFORMATION_MESSAGE);
			
			this.updateWalletAddressBalanceTable();
		} catch (Exception e)
		{
			e.printStackTrace();			
			AddressesPanel.this.errorReporter.reportError(e, false);
		}
	}
	

	private void updateWalletAddressBalanceTable()
		throws WalletCallException, IOException, InterruptedException
	{
		String[][] newAddressBalanceData = this.getAddressBalanceDataFromWallet();

		//if (lastAddressBalanceData.length != newAddressBalanceData.length) -always refreshed
		{
			this.remove(addressBalanceTablePane);
			this.add(addressBalanceTablePane = new JScrollPane(
			             addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)),
			         BorderLayout.CENTER);
		}

		lastAddressBalanceData = newAddressBalanceData;

		this.validate();
		this.repaint();
	}


	private JTable createAddressBalanceTable(String rowData[][])
		throws WalletCallException, IOException, InterruptedException
	{
		String columnNames[] = { "Balance", "Confirmed?", "Address" };
        JTable table = new DataTable(rowData, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(1000);

        return table;
	}


	private String[][] getAddressBalanceDataFromWallet()
		throws WalletCallException, IOException, InterruptedException
	{
		// Z Addresses - they are OK
		String[] zAddresses = clientCaller.getWalletZAddresses();
		
		// T Addresses listed with the list received by addr comamnd
		String[] tAddresses = this.clientCaller.getWalletAllPublicAddresses();
		Set<String> tStoredAddressSet = new HashSet<>();
		for (String address : tAddresses)
		{
			tStoredAddressSet.add(address);
		}
		
		// T addresses with unspent outputs - just in case they are different
		String[] tAddressesWithUnspentOuts = this.clientCaller.getWalletPublicAddressesWithUnspentOutputs();
		Set<String> tAddressSetWithUnspentOuts = new HashSet<>();
		for (String address : tAddressesWithUnspentOuts)
		{
			tAddressSetWithUnspentOuts.add(address);
		}
		
		// Combine all known T addresses
		Set<String> tAddressesCombined = new HashSet<>();
		tAddressesCombined.addAll(tStoredAddressSet);
		tAddressesCombined.addAll(tAddressSetWithUnspentOuts);
		
		String[][] addressBalances = new String[zAddresses.length + tAddressesCombined.size()][];
		
		int i = 0;

		for (String address : tAddressesCombined)
		{
			String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));
			
			// TODO: format balance
			
			addressBalances[i++] = new String[] 
			{  
				isConfirmed ? confirmedBalance : unconfirmedBalance,
				isConfirmed ? "Yes \u2690" : "No  \u2691",
				address
			};
		}
		
		for (String address : zAddresses)
		{
			// TODO: check for wrong/negative balance, - maybe address does not exist
			String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));
			
			// TODO: format balance
			
			addressBalances[i++] = new String[] 
			{  
				isConfirmed ? confirmedBalance : unconfirmedBalance,
				isConfirmed ? "Yes \u2690" : "No  \u2691",
				address
			};
		}

		return addressBalances;
	}	

}
