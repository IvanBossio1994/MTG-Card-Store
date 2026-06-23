using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace TCGInventoryShell
{
    internal static class Program
    {
        private const string AppName = "TCG Inventory";
        private const string JarName = "tcg-inventory-bot-0.0.1-SNAPSHOT.jar";

        [STAThread]
        private static async Task Main()
        {
            Application.SetHighDpiMode(HighDpiMode.SystemAware);
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Process? backend = null;
            try
            {
                var root = FindProjectRoot();
                var jarPath = Path.Combine(root, "target", JarName);
                if (!File.Exists(jarPath))
                {
                    Fail("No encontre el JAR de la app. Ejecuta primero: mvn package");
                    return;
                }

                var javaPath = FindExecutable("java.exe", new[]
                {
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Eclipse Adoptium"),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Java")
                });
                if (javaPath == null)
                {
                    Fail("No encontre Java 21 en esta PC.");
                    return;
                }

                var edgePath = FindEdge();
                if (edgePath == null)
                {
                    Fail("No encontre Microsoft Edge en esta PC.");
                    return;
                }

                var port = FreePort();
                var url = $"http://127.0.0.1:{port}/";
                backend = StartBackend(javaPath, jarPath, root, port);

                if (!await WaitForApp(url, TimeSpan.FromSeconds(45)))
                {
                    Fail("La app no termino de iniciar. Revisa desktop-shell.log.");
                    return;
                }

                var edge = StartEdge(edgePath, root, url);
                edge.WaitForExit();
            }
            catch (Exception ex)
            {
                Fail(ex.Message);
            }
            finally
            {
                if (backend != null && !backend.HasExited)
                {
                    backend.Kill(entireProcessTree: true);
                }
            }
        }

        private static string FindProjectRoot()
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory != null)
            {
                if (File.Exists(Path.Combine(directory.FullName, "pom.xml")))
                {
                    return directory.FullName;
                }

                directory = directory.Parent;
            }

            throw new InvalidOperationException("No encontre la carpeta del proyecto.");
        }

        private static Process StartBackend(string javaPath, string jarPath, string root, int port)
        {
            var logPath = Path.Combine(root, "target", "desktop-shell.log");
            Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);

            var process = new Process();
            process.StartInfo.FileName = javaPath;
            process.StartInfo.Arguments = $"-jar \"{jarPath}\" --server.port={port}";
            process.StartInfo.WorkingDirectory = root;
            process.StartInfo.UseShellExecute = false;
            process.StartInfo.CreateNoWindow = true;
            process.StartInfo.RedirectStandardOutput = true;
            process.StartInfo.RedirectStandardError = true;
            process.OutputDataReceived += (_, e) => AppendLog(logPath, e.Data);
            process.ErrorDataReceived += (_, e) => AppendLog(logPath, e.Data);
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            return process;
        }

        private static Process StartEdge(string edgePath, string root, string url)
        {
            var profileDir = Path.Combine(root, "target", "edge-shell-profile");
            Directory.CreateDirectory(profileDir);

            var process = new Process();
            process.StartInfo.FileName = edgePath;
            process.StartInfo.Arguments = $"--app=\"{url}\" --user-data-dir=\"{profileDir}\" --no-first-run";
            process.StartInfo.UseShellExecute = false;
            process.Start();
            return process;
        }

        private static async Task<bool> WaitForApp(string url, TimeSpan timeout)
        {
            using var client = new HttpClient();
            var until = DateTimeOffset.UtcNow.Add(timeout);
            while (DateTimeOffset.UtcNow < until)
            {
                try
                {
                    using var response = await client.GetAsync(url);
                    if ((int)response.StatusCode < 500)
                    {
                        return true;
                    }
                }
                catch
                {
                    Thread.Sleep(500);
                }
            }

            return false;
        }

        private static int FreePort()
        {
            var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            var port = ((IPEndPoint)listener.LocalEndpoint).Port;
            listener.Stop();
            return port;
        }

        private static string? FindEdge()
        {
            var candidates = new[]
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Microsoft", "Edge", "Application", "msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft", "Edge", "Application", "msedge.exe")
            };

            return candidates.FirstOrDefault(File.Exists);
        }

        private static string? FindExecutable(string executable, string[] roots)
        {
            var pathMatch = Environment.GetEnvironmentVariable("PATH")
                ?.Split(Path.PathSeparator)
                .Select(path => Path.Combine(path, executable))
                .FirstOrDefault(File.Exists);
            if (pathMatch != null)
            {
                return pathMatch;
            }

            return roots
                .Where(Directory.Exists)
                .SelectMany(root => Directory.EnumerateFiles(root, executable, SearchOption.AllDirectories))
                .FirstOrDefault();
        }

        private static void AppendLog(string logPath, string? line)
        {
            if (line == null)
            {
                return;
            }

            File.AppendAllText(logPath, line + Environment.NewLine);
        }

        private static void Fail(string message)
        {
            MessageBox.Show(message, AppName, MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
