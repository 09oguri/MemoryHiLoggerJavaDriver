import java.net.*;
import java.io.*;

public class Samp16 {
	// スタート状態とPCのMAC要求コマンド
	byte[] REQUIRE_MAC = {(byte) 0x02, (byte) 0x01, (byte) 0x5b, (byte) 0x00, (byte) 0x00};
	// 記録間隔（高速側）の設定を取得
	byte[] SAMP = {(byte) 0x02, (byte) 0x20, (byte) 0x01, (byte) 0x00, (byte) 0x00};
	// 記録間隔（高速側）を10msに設定
	byte[] SAMP_10ms = {(byte) 0x02, (byte) 0x20, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00};
	// 記録間隔（高速側）を50msに設定
	byte[] SAMP_50ms = {(byte) 0x02, (byte) 0x20, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x02};
	// 記録間隔（高速側）を100msに設定
	byte[] SAMP_100ms = {(byte) 0x02, (byte) 0x20, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x03};
	// スタートコマンド(後ろ6バイトはPCのMACアドレス)
	byte[] START = {(byte) 0x02, (byte) 0x01, (byte) 0x50, (byte) 0x00, (byte) 0x06
			, (byte) 0x00, (byte) 0x1a, (byte) 0x4d, (byte) 0x5b, (byte) 0x15, (byte) 0x3c};
	// メモリ内先頭番号、データ数要求コマンド1st（高速）
	byte[] REQUIRE_MEMORY = {(byte) 0x02, (byte) 0x01, (byte) 0x53, (byte) 0x00, (byte) 0x00};
	// 測定状態要求コマンド
	byte[] REQUIRE_STATE = {(byte) 0x02, (byte) 0x01, (byte) 0x57, (byte) 0x00, (byte) 0x00};
	// アプリシステムトリガコマンド
	byte[] SYSTRIGGER = {(byte) 0x02, (byte) 0x01, (byte) 0x58, (byte) 0x00, (byte) 0x09
			, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
			, (byte) 0x00};
	// データ要求コマンド1st（高速）
	byte[] REQUIRE_DATA = {(byte) 0x02, (byte) 0x01, (byte) 0x55, (byte) 0x00, (byte) 0x10
			, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
			, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01};
	// ストップコマンド
	byte[] STOP = {(byte) 0x02, (byte) 0x01, (byte) 0x51, (byte) 0x00, (byte) 0x00};
	
	String hostname = "192.168.172.2";
	int port = 8802;
	
	int max_ch = 2;
	int max_unit = 1;
	
	
	public static void main(String[] args) {
		Samp16 app = new Samp16();
		app.communication();
	}
	
	
	private void communication() {
		try{
			Socket theSocket = new Socket(hostname, port);
			
			InputStream is;
			InputStreamReader isr;
			BufferedReader br;
			OutputStream os;
			

			// ソケットオープン時の応答処理
			is = theSocket.getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			while(is.available() == 0);
			char[] line = new char[is.available()];
			br.read(line);
			System.out.println(line);
			
			
			os = theSocket.getOutputStream();
			
			command(is, os, SAMP_50ms);
			command(is, os, START);
			Thread.sleep(1000);

			int ret = -1;
			while(ret != 65){
				ret = command(is, os, REQUIRE_STATE, false);
				System.out.print(".");
			}
			System.out.println("");
			ret = -1;
			
			command(is, os, SYSTRIGGER);
			Thread.sleep(1000);
			
			while(ret != 35){
				ret = command(is, os, REQUIRE_STATE, false);
				System.out.print(".");
			}
			System.out.println("");
			ret = -1;
			
			command(is, os, REQUIRE_MEMORY);
			
			command(is, os, REQUIRE_DATA);
			
			//データ取得5-12
			//int j = 1;
			byte[] req = REQUIRE_DATA;
			
			while(is.available() == 0);
			byte[] rec = new byte[is.available()];
			for(int i = 0; i < 4095; i++){
				is.read(rec);
				readable(rec);
				
				/*
				if(i < 256){
					req[12] = (byte) i;
				}else if(i < 65535){
					if(i % 256 == 0){
						req[11] = (byte) j;
						j++;
					}
					req[12] = (byte) (i - 256 * j);
				}
				*/
				
				for(int j = 12; j > 5; j--){
					if(req[j] == 0xffffffff){
						req[j] = 0x00000000;
						req[j-1]++;
						if(req[j-1] != 0xffffffff){
							break;
						}
					}else if(req[5] == 0xff){
						return;
					}else{
						req[12]++;
						break;
					}
				}
				
				
				Thread.sleep(50);	// 測定間隔よりも速くならないように
				command(is, os, req);
			}
			
			command(is, os, STOP);
			
			while(ret != 0){
				ret = command(is, os, REQUIRE_STATE, false);
				System.out.print(".");
			}
			System.out.println("");
			ret = -1;
			
			theSocket.close();
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	// コマンド実行
	private int command(InputStream is, OutputStream os, byte[] msg){		
		sendMsg(os, msg);
		return getMsg(is, true);
	}
	
	// コマンド実行（ログ表示・非表示）
	private int command(InputStream is, OutputStream os, byte[] msg, boolean log){
		sendMsg(os, msg);
		return getMsg(is, log);
	}
	
	// コマンド送信
	private void sendMsg(OutputStream os, byte[] msg){
		try{
			os.write(msg);
			os.flush();
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	// コマンド受信
	private int getMsg(InputStream is, boolean log){
		try{
			while(is.available() == 0);
			byte[] rec = new byte[is.available()];
			is.read(rec);
			
			if(log){
				return readable(rec);	// 応答を表示
			}else{
				return readret(rec);	// 応答非表示
			}
		}catch(Exception e){
			e.printStackTrace();
			System.exit(1);
			return -1;
		}
	}
	
	
	private int readable(byte[] rec){
		if(rec.length < 1){
			return -1;
		}
		switch(rec[0]){
		case 0x01:
			switch(rec[1]){
			case 0x00:
				switch(rec[2]){
				case 0x01:
					System.out.println("データ転送コマンド1st:" + System.currentTimeMillis());
					System.out.print("\tサンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tハンドシェイク時の残りデータ数:\t");
					for(int i = 13; i < 21; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					int index = 21;
					for(int unit = 1; unit < 9; unit++){
						for(int ch = 1; ch < 17; ch++){
							if(ch <= max_ch && unit <= max_unit)
								System.out.print("\tU" + unit + "CH" + ch + ":\t");
							for(int i = 0; i < 4; i++){
								if(ch <= max_ch && unit <= max_unit)
									System.out.print(Integer.toHexString(rec[index]) + " ");
								index++;
							}
							if(ch <= max_ch && unit <= max_unit)
								System.out.println("");
						}
					}
					System.out.println("");
					return 0;	// (FIXME)電圧データを返すようにする
				default:
					for(int i = 0; i < rec.length; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;	
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++){
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		case 0x02:
			switch(rec[1]){
			case 0x01:
				switch(rec[2]){
				case 0x50:
					System.out.print("スタートコマンド:\t");
					if(rec[5] == 0x000)
						System.out.println("OK");
					else if(rec[5] == 0x01)
						System.out.println("既にスタート中");
					else
						System.out.println("UNIT数が9個以上でスタートできません");
					return rec[5];
				case 0x51:
					System.out.print("ストップコマンド:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return rec[5];
				case 0x53:
					System.out.println("メモリ内先頭番号、データ数要求コマンド1st（高速）:");
					System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					return 0;	// (FIXME?)
				case 0x55:
					System.out.println("データ要求コマンド1st:");
					System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tデータ転送サンプル番号:\t");
					for(int i = 21; i < 29; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\tデータ数:\t");
					for(int i = 29; i < 37; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\t測定状態:\t");
					System.out.println(Integer.toHexString(rec[37]));
					return rec[37];
				case 0x57:
					System.out.print("測定状態要求コマンド:\t");
					if(rec[5] == 0x00)
						System.out.println("スタート前またはスタート動作終わり");
					else if(rec[5] == 0x41)
						System.out.println("アプリシステムトリガ待ち");
					else if(rec[5] == 0x23)
						System.out.println("リアルタイムセーブ中");
					else
						System.out.println(Integer.toHexString(rec[5]));
					return rec[5];
				case 0x58:
					System.out.print("アプリシステムトリガコマンド:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return rec[5];
				case 0x5b:
					System.out.println("スタート状態とPCのMAC要求コマンド:");
					System.out.print("\tスタート状態:\t");
					if(rec[5] == 0)
						System.out.println("STOP");
					else
						System.out.println("START");
					System.out.print("\tMAC:\t");
					for(int i = 6; i < 12; i++){
						System.out.print(Integer.toHexString(rec[i]) + ":");
					}
					System.out.println("");
					return rec[5];
				default:
					for(int i = 0; i < rec.length; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;
				}
				break;
			case 0x20:
				switch(rec[2]){
				case 0x01:
					System.out.print("記録間隔（高速側）:\t");
					if(rec[5] == 0x00)
						System.out.println("10ms");
					else if(rec[5] == 0x01)
						System.out.println("20ms");
					else if(rec[5] == 0x02)
						System.out.println("50ms");
					else if(rec[5] == 0x03)
						System.out.println("100ms");
					else
						System.out.println("200ms以上");
					return rec[5];
				default:
					break;
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++){
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		default:
			for(int i = 0; i < rec.length; i++){
				System.out.print(Integer.toHexString(rec[i]) + " ");
			}
			break;
		}
		System.out.println("");
		return -1;
	}
	
	
	private int readret(byte[] rec){
		if(rec.length < 1){
			return -1;
		}
		switch(rec[0]){
		case 0x01:
			switch(rec[1]){
			case 0x00:
				switch(rec[2]){
				case 0x01:
					//System.out.println("データ転送コマンド1st:" + System.currentTimeMillis());
					//System.out.print("\tサンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\tハンドシェイク時の残りデータ数:\t");
					for(int i = 13; i < 21; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//int index = 21;
					for(int unit = 1; unit < 9; unit++){
						for(int ch = 1; ch < 17; ch++){
							if(ch <= max_ch && unit <= max_unit);
								//System.out.print("\tU" + unit + "CH" + ch + ":\t");
							for(int i = 0; i < 4; i++){
								if(ch <= max_ch && unit <= max_unit);
									//System.out.print(Integer.toHexString(rec[index]) + " ");
								//index++;
							}
							if(ch <= max_ch && unit <= max_unit);
								//System.out.println("");
						}
					}
					//System.out.println("");
					return 0;	// (FIXME)電圧データを返すようにする
				default:
					for(int i = 0; i < rec.length; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;	
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++){
					//System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		case 0x02:
			switch(rec[1]){
			case 0x01:
				switch(rec[2]){
				case 0x50:
					//System.out.print("スタートコマンド:\t");
					if(rec[5] == 0x000);
						//System.out.println("OK");
					else if(rec[5] == 0x01);
						//System.out.println("既にスタート中");
					else;
						//System.out.println("UNIT数が9個以上でスタートできません");
					return rec[5];
				case 0x51:
					//System.out.print("ストップコマンド:\t");
					if(rec[5] == 0);
						//System.out.println("OK");
					else;
						//System.out.println("NG");
					return rec[5];
				case 0x53:
					//System.out.println("メモリ内先頭番号、データ数要求コマンド1st（高速）:");
					//System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					return 0;	// (FIXME?)
				case 0x55:
					//System.out.println("データ要求コマンド1st:");
					//System.out.print("\tメモリ内先頭サンプリング番号:\t");
					for(int i = 5; i < 13; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\tメモリ内データ数:\t");
					for(int i = 13; i < 21; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\tデータ転送サンプル番号:\t");
					for(int i = 21; i < 29; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\tデータ数:\t");
					for(int i = 29; i < 37; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					//System.out.println("");
					//System.out.print("\t測定状態:\t");
					//System.out.println(Integer.toHexString(rec[37]));
					return rec[37];
				case 0x57:
					//System.out.print("測定状態要求コマンド:\t");
					if(rec[5] == 0x00);
						//System.out.println("スタート前またはスタート動作終わり");
					else if(rec[5] == 0x41);
						//System.out.println("アプリシステムトリガ待ち");
					else if(rec[5] == 0x23);
						//System.out.println("リアルタイムセーブ中");
					else;
						//System.out.println(Integer.toHexString(rec[5]));
					return rec[5];
				case 0x58:
					//System.out.print("アプリシステムトリガコマンド:\t");
					if(rec[5] == 0);
						//System.out.println("OK");
					else;
						//System.out.println("NG");
					return rec[5];
				case 0x5b:
					//System.out.println("スタート状態とPCのMAC要求コマンド:");
					//System.out.print("\tスタート状態:\t");
					if(rec[5] == 0);
						//System.out.println("STOP");
					else;
						//System.out.println("START");
					//System.out.print("\tMAC:\t");
					for(int i = 6; i < 12; i++){
						//System.out.print(Integer.toHexString(rec[i]) + ":");
					}
					//System.out.println("");
					return rec[5];
				default:
					for(int i = 0; i < rec.length; i++){
						//System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;
				}
				break;
			case 0x20:
				switch(rec[2]){
				case 0x01:
					//System.out.print("記録間隔（高速側）:\t");
					if(rec[5] == 0x00);
						//System.out.println("10ms");
					else if(rec[5] == 0x01);
						//System.out.println("20ms");
					else if(rec[5] == 0x02);
						//System.out.println("50ms");
					else if(rec[5] == 0x03);
						//System.out.println("100ms");
					else;
						//System.out.println("200ms以上");
					return rec[5];
				default:
					break;
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++){
					//System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		default:
			for(int i = 0; i < rec.length; i++){
				//System.out.print(Integer.toHexString(rec[i]) + " ");
			}
			break;
		}
		//System.out.println("");
		return -1;
	}

}
