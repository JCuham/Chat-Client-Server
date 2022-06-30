using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shapes;
using System.Threading;
using System.Net;
using System.Net.Sockets;

namespace ChatClient
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {

        TcpClient clientSocket = new TcpClient();
        NetworkStream serverStream = default(NetworkStream);
        string readData = null;
        public MainWindow()
        {
            InitializeComponent();
            

        }

        //opens a socket and connects to the chat server
        private void buttonConnect_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                //Remove login frame and set name of client window to "Nickname's Chat Client"
                loginFrame.Visibility = Visibility.Hidden;
                string nickName = textBoxEnterName.Text.ToString();

                this.Title = nickName + "'s Chat Client";
                //start connection
                readData = "Connected to Server...";
                outputmsg();
                clientSocket.Connect("127.0.0.1", 8888);
                serverStream = clientSocket.GetStream();

                //send name to server
                byte[] outStream = System.Text.Encoding.ASCII.GetBytes("/nickname " + textBoxEnterName.Text.ToString() + "\n");

                serverStream.Write(outStream, 0, outStream.Length);
                serverStream.Flush();

                //start client thread listening to messages from server
                Thread ctThread = new Thread(getMessage);
                ctThread.Start();
            }
            catch (Exception ex) {
                MessageBox.Show("Error connecting to server: " + ex.ToString());
            }
        }

        private void textBoxEnterName_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            textBoxEnterName.Clear();
        }

        private void btnSendMsg_Click(object sender, RoutedEventArgs e)
        {
            byte[] outStream = System.Text.Encoding.ASCII.GetBytes(chatBox.Text.ToString() + "\n");
            serverStream.Write(outStream, 0, outStream.Length);
            serverStream.Flush();
            chatBox.Clear();
        }

        private void getMessage() {
            while ((true)) {

                serverStream = clientSocket.GetStream();
                int buffSize = 0;
                byte[] inStream = new byte[65537];
                buffSize = clientSocket.ReceiveBufferSize;
                serverStream.Read(inStream, 0, buffSize);

                string returndata = System.Text.Encoding.ASCII.GetString(inStream);
                readData = "" + returndata;
                outputmsg();
            }
        }

        private void outputmsg() {
            if (this.Dispatcher.CheckAccess()==false)
            {
                this.Dispatcher.Invoke(outputmsg);
            }
            else
            {           
                displayMsgBox.AppendText(readData + "\n");

            }
        }
    }
}
