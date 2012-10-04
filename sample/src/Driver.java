import java.net.*;
import java.util.ArrayList;
import java.io.*;


public class Driver {
	
	static final String HOSTNAME = "192.168.172.2";
	static final int PORT = 8802;
	static final int INTERVAL = 10;	// (TODO) �x�������Ȃ�����悤�ɓ��I�ɐݒ�
	
	// �v������`�����l�����A���j�b�g��
	// �ύX����ꍇ�̓������n�C���K�[���Đݒ肷��K�v������
	// ���j�b�g���ɕʁX�̃`�����l������ݒ�ł��Ȃ�
	static final int MAX_CH = 10;	// �ő�14ch
	static final int MAX_UNIT = 1;	// �ő�8unit
	
	private Socket theSocket;
	private InputStream is;
	private InputStreamReader isr;
	private BufferedReader br;
	private OutputStream os;
	
	private ArrayList<Double> volt = new ArrayList<Double>();	// �擾�����d��
	private ArrayList<Double> power = new ArrayList<Double>();	// �d������v�Z��������d��
	
	private byte[] req;	// �f�[�^�v���R�}���h
	private byte[] raw;	// �d���̐��f�[�^
	private int intrval = INTERVAL;
	private long count = 0;
	
	
	public static void main(String[] args) {
		Driver app = new Driver();
		app.communication();
	}
	
	
	// ������
	private void init() {
		try{
			theSocket = new Socket(HOSTNAME, PORT);
			theSocket.setSoTimeout(INTERVAL + 1000);
			
			// �\�P�b�g�I�[�v�����̉�������
			is = theSocket.getInputStream();
			isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			while(is.available() == 0);
			char[] line = new char[is.available()];
			br.read(line);
			System.out.println(line);
			
			os = theSocket.getOutputStream();
			
			// �f�[�^�̎擾�Ԋu��ݒ�
			if(INTERVAL == 10) {
				command(Command.SAMP_10ms);
			}else if(INTERVAL == 50) {
				command(Command.SAMP_50ms);
			}else {
				command(Command.SAMP_100ms);
			}
			
			// �X�^�[�g
			readable(command(Command.START));
			Thread.sleep(1000);	// ��Ԃ��ω�����̂�҂�
			byte rec = (byte) 0xff;
			while(rec != 65){
				rec = getState(command(Command.REQUIRE_STATE));
			}
			rec = (byte) 0xff;
			
			// �V�X�e���g���K�[
			readable(command(Command.SYSTRIGGER));
			Thread.sleep(1000);
			while(rec != 35){
				rec = getState(command(Command.REQUIRE_STATE));
			}
			rec = (byte) 0xff;
			
			// �f�[�^�v��
			req = Command.REQUIRE_DATA;
			readable(command(req));
			while(is.available() == 0);
			raw = new byte[is.available()];
			is.read(raw);
			
			Thread.sleep(INTERVAL);
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	// �I�����̏���
	private void end() {
		if(theSocket != null) {
			try {
				// �X�g�b�v
				readable(command(Command.STOP));
				theSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private void communication() {
		init();
		try{
			while(true) {
				getData();
				for(int i = 0; 0 < power.size(); i++) {
					System.out.println("����d��" + (i + 1) + "�F" + power.get(0) + "[W]");
					power.remove(0);
				}
				volt.clear();
			}
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}finally {
			end();
		}
	}
	
	
	// �R�}���h���s
	private byte[] command(byte[] msg) {
		sendMsg(msg);
		return getMsg();
	}
	
	
	// MemoryHiLogger����d���f�[�^���󂯎�����d�͂��v�Z
	private void getData() throws InterruptedException, IOException {
		try {
			byte[] rec = command(req);	// �f�[�^�v���R�}���h
			/*
			 * �x������
			if(getNumData(rec) - count > 100 && intrval > 0) {
				intrval -= 1;
			}else {
				intrval = INTERVAL;
				System.out.println(getNumData(rec) - count);
			}
			*/
			is.read(raw);	// �f�[�^�]���R�}���h���󂯎��
			getVolt(raw);	// �󂯎�����f�[�^��d���ɕϊ�
			getPower();	// �d���������d�͂��v�Z
			incRequireCommand();	// �f�[�^�v�����̃T���v�����O�ԍ����P�i�߂�
			Thread.sleep(intrval);	// �f�[�^�̎擾�Ԋu
		}catch(Exception e) {
			e.printStackTrace();
			end();
			System.exit(1);
		}
	}
	
	
	//  MemoryHiLogger�ɃR�}���h�𑗐M
	private void sendMsg(byte[] msg) {
		try{
			os.write(msg);
			os.flush();
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	
	// MemoryHiLogger����R�}���h����M
	private byte[] getMsg() {
		try{
			while(is.available() == 0);
			byte[] rec = new byte[is.available()];
			is.read(rec);
			return rec;
		}catch(Exception e) {
			e.printStackTrace();
			end();
			System.exit(1);
			return null;
		}
	}
	
	
	// ���f�[�^����d�����X�g���擾
	private void getVolt(byte[] rec) {
		String raw = "";
		int index = 21;
		if(rec[0] == 0x01 && rec[1] == 0x00 && rec[2] == 0x01) {	// �f�[�^�]���R�}���h
			for(int unit = 1; unit < 9; unit++) {
				for(int ch = 1; ch < 17; ch++) {
					for(int i = 0; i < 4; i++) {	// �X�̓d��
						if(ch <= MAX_CH && unit <= MAX_UNIT) {
							raw += String.format("%02x", rec[index]);
						}
						index++;
					}
					if(ch <= MAX_CH && unit <= MAX_UNIT) {
						// �d���l�ɕϊ�(�X���C�hp47)
						// �d���������W
						// �����F 1(V/10DIV)
						// ���K�[���[�e�B���e�B�F 100 mv f.s. -> 0.1(V/10DIV)???
						volt.add(((double) Integer.parseInt(raw, 16) - 32768.0) * 0.1 / 20000.0);
					}
					raw = "";
				}
			}
		}else {	// �f�[�^�]���R�}���h�łȂ��ꍇ
			volt = null;
		}
	}
	
	
	// �d�����X�g�������d�̓��X�g���擾
	private void getPower() {
		int voltListSize = volt.size();
		
		if(voltListSize % 2 != 0) {
			voltListSize--;
		}
		for(int i = 0; i < voltListSize; i += 2) {
			// (TODO) �ǂ����̃`�����l����12V��5V�Ȃ̂��𔻕ʂł���悤�ɂ���K�v������
			power.add(Math.abs(volt.get(i)) * 120.0 + Math.abs(volt.get(i + 1)) * 50.0);
		}
	}
	
	
	// MemoryHiLogger�̏�Ԃ��擾
	private byte getState(byte[] rec) {
		if(rec[0] == 0x02 && rec[1] == 0x01) {
			switch(rec[2]) {
			case 0x50:	// �X�^�[�g�R�}���h
			case 0x51:	// �X�g�b�v�R�}���h
			case 0x57:	// �����ԗv���R�}���h
			case 0x58:	// �A�v���V�X�e���g���K�R�}���h
				return rec[5];
			}
		}
		return (byte) 0xff;	// �s���ȃR�}���h
	}
	
	
	// ���������f�[�^�����擾
	private long getNumData(byte[] rec) {
		String raw = "";
		if(rec[0] == 0x02 && rec[1] == 0x01) {
			switch(rec[2]) {
			case 0x55:
				for(int i = 13; i < 21; i++) {
					raw += String.format("%02x", rec[i]);
				}
				return Long.parseLong(raw, 16);
			}
		}
		return -1;
	}
	
	
	// �f�[�^�v���R�}���h�̃T���v�����O�ԍ��̃C���N�������g
	private void incRequireCommand() {
		count++;
		for(int i = 12; i > 5; i--) {
			if(req[i] == 0xffffffff) {
				req[i] = 0x00000000;
				req[i - 1]++;
				if(req[i - 1] != 0xffffffff) {
					break;
				}
			}else if(req[5] == 0xffffffff) {
				return;
			}else {
				req[12]++;
				break;
			}
		}
	}
	
	
	// �ǂ߂�悤�ɕ\������
	private void readable(byte[] rec) {
		String raw = "";
		if(rec.length < 1) {
			return;
		}
		switch(rec[0]) {
		case 0x01:
			switch(rec[1]) {
			case 0x00:
				switch(rec[2]) {
				case 0x01:
					raw = "";
					System.out.println("�f�[�^�]���R�}���h1st:" + System.currentTimeMillis());
					System.out.print("\t�T���v�����O�ԍ�:\t");
					for(int i = 5; i < 13; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t�n���h�V�F�C�N���̎c��f�[�^��:\t");
					for(int i = 13; i < 21; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					int index = 21;
					raw = "";
					double volt = 0;
					for(int unit = 1; unit < 9; unit++) {
						for(int ch = 1; ch < 17; ch++) {
							if(ch <= MAX_CH && unit <= MAX_UNIT)
								System.out.print("\tU" + unit + "CH" + ch + ":\t");
							for(int i = 0; i < 4; i++) {	// �X�̓d��
								if(ch <= MAX_CH && unit <= MAX_UNIT) {
									raw += String.format("%02x", rec[index]);
								}
								index++;
							}
							if(ch <= MAX_CH && unit <= MAX_UNIT) {
								// �d���l�ɕϊ�(�X���C�hp47)
								// �d���������W
								// 	�����F 1(V/10DIV)
								//	���K�[���[�e�B���e�B�F 100 mv f.s. -> 0.1(V/10DIV)???
								volt = ((double) Integer.parseInt(raw, 16) - 32768.0) * 0.1 / 20000.0;
								System.out.printf("%.5f [v]\n", volt);
							}
							raw = "";
						}
					}
					System.out.println("");
					return;
				default:
					for(int i = 0; i < rec.length; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;	
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++) {
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		case 0x02:
			switch(rec[1]) {
			case 0x01:
				switch(rec[2]) {
				case 0x50:
					System.out.print("�X�^�[�g�R�}���h:\t");
					if(rec[5] == 0x000)
						System.out.println("OK");
					else if(rec[5] == 0x01)
						System.out.println("���ɃX�^�[�g��");
					else
						System.out.println("UNIT����9�ȏ�ŃX�^�[�g�ł��܂���");
					return;
				case 0x51:
					System.out.print("�X�g�b�v�R�}���h:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return;
				case 0x53:
					System.out.println("���������擪�ԍ��A�f�[�^���v���R�}���h1st�i�����j:");
					System.out.print("\t���������擪�T���v�����O�ԍ�:\t");
					for(int i = 5; i < 13; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					System.out.print("\t���������f�[�^��:\t");
					for(int i = 13; i < 21; i++){
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					System.out.println("");
					return;
				case 0x55:
					raw = "";
					System.out.println("�f�[�^�v���R�}���h1st:" +  + System.currentTimeMillis());
					System.out.print("\t���������擪�T���v�����O�ԍ�:\t");
					for(int i = 5; i < 13; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t���������f�[�^��:\t");
					for(int i = 13; i < 21; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t�f�[�^�]���T���v���ԍ�:\t");
					for(int i = 21; i < 29; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t�f�[�^��:\t");
					for(int i = 29; i < 37; i++) {
						raw += String.format("%02x", rec[i]);
					}
					System.out.println(Integer.parseInt(raw, 16) + ": " + raw);
					raw = "";
					System.out.print("\t������:\t");
					System.out.println(Integer.toHexString(rec[37]));
					return;
				case 0x57:
					System.out.print("�����ԗv���R�}���h:\t");
					if(rec[5] == 0x00)
						System.out.println("�X�^�[�g�O�܂��̓X�^�[�g����I���");
					else if(rec[5] == 0x41)
						System.out.println("�A�v���V�X�e���g���K�҂�");
					else if(rec[5] == 0x23)
						System.out.println("���A���^�C���Z�[�u��");
					else
						System.out.println(Integer.toHexString(rec[5]));
					return;
				case 0x58:
					System.out.print("�A�v���V�X�e���g���K�R�}���h:\t");
					if(rec[5] == 0)
						System.out.println("OK");
					else
						System.out.println("NG");
					return;
				case 0x5b:
					System.out.println("�X�^�[�g��Ԃ�PC��MAC�v���R�}���h:");
					System.out.print("\t�X�^�[�g���:\t");
					if(rec[5] == 0)
						System.out.println("STOP");
					else
						System.out.println("START");
					System.out.print("\tMAC:\t");
					for(int i = 6; i < 12; i++) {
						System.out.print(Integer.toHexString(rec[i]) + ":");
					}
					System.out.println("");
					return;
				default:
					for(int i = 0; i < rec.length; i++) {
						System.out.print(Integer.toHexString(rec[i]) + " ");
					}
					break;
				}
				break;
			case 0x20:
				switch(rec[2]) {
				case 0x01:
					System.out.print("�L�^�Ԋu�i�������j:\t");
					if(rec[5] == 0x00)
						System.out.println("10ms");
					else if(rec[5] == 0x01)
						System.out.println("20ms");
					else if(rec[5] == 0x02)
						System.out.println("50ms");
					else if(rec[5] == 0x03)
						System.out.println("100ms");
					else
						System.out.println("200ms�ȏ�");
					return;
				default:
					break;
				}
				break;
			default:
				for(int i = 0; i < rec.length; i++) {
					System.out.print(Integer.toHexString(rec[i]) + " ");
				}
				break;
			}
			break;
		default:
			for(int i = 0; i < rec.length; i++) {
				System.out.print(Integer.toHexString(rec[i]) + " ");
			}
			break;
		}
		System.out.println("");
		return;
	}

}
